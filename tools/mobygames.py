#!/usr/bin/env python3
"""Dump MobyGames records for the games of chosen platforms, for reference.

The listings are MobyPlus exports saved as <platform>-listing.json in the output directory, one
JSON list per platform; PLATFORMS gives each Kairo platform slug's MobyGames platform ID. Passes
run over all chosen platforms in order: `details` saves each game's record and its release
record on each platform; `art` saves each game's cover and screenshot lists on each platform,
then downloads every image they name, eight at a time from the CDN. Each response is its own
file under the output directory, so a stopped run resumes where it left off. API calls are made
one per second and wait for the hourly window to reset when the API reports it used up. A 404 is
saved as that item's result; any other refusal stops the run. --games-from limits the passes to
the games a JSON file names, {"<platform slug>": [MobyGames ID, ...]}, such as the file research.py
writes when a game's artwork lists are not dumped yet.

While the network is down, or a request goes two minutes without an answer, it waits and
asks again every 30 seconds, so it carries on by itself when the connection returns.

Reads the API key from MOBYGAMES_API_KEY.
"""

import argparse
import http.client
import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

API = "https://api.mobygames.com/v1"
AGENT = {"User-Agent": "Kairo-catalog/1.0"}
# Kairo platform slug -> MobyGames platform ID.
PLATFORMS = {"pc98": 95, "pc88": 94, "pcfx": 59, "x68000": 106, "fmtowns": 102, "msx": 57}
# Chosen by the user: a request with no response for this long counts as a dropped connection.
REQUEST_LIMIT_SECONDS = 120
OFFLINE_RECHECK_SECONDS = 30


def fetch(url):
    """The body and headers at a URL. While the network is down, or a request is cut off,
    waits and asks again until it answers. MobyGames' own refusals raise HTTPError."""
    offline = False
    while True:
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=AGENT),
                                        timeout=REQUEST_LIMIT_SECONDS) as response:
                body = response.read()
                if offline:
                    print("Network is back", flush=True)
                return body, response.headers
        except urllib.error.HTTPError:
            raise
        except (urllib.error.URLError, OSError, http.client.HTTPException) as error:
            if not offline:
                print(f"Network unavailable ({error}); waiting for it to return", flush=True)
                offline = True
            time.sleep(OFFLINE_RECHECK_SECONDS)


class Api:
    def __init__(self, key):
        self.key = key
        self.last = 0.0

    def get(self, path):
        """The JSON at an API path, or {"error": 404} when MobyGames has no such record."""
        wait = self.last + 1.0 - time.monotonic()
        if wait > 0:
            time.sleep(wait)
        url = f"{API}{path}?{urllib.parse.urlencode({'api_key': self.key})}"
        try:
            payload, headers = fetch(url)
            self.last = time.monotonic()
            body = json.loads(payload)
            remaining = headers.get("ratelimit-remaining")
            reset = headers.get("ratelimit-reset")
        except urllib.error.HTTPError as error:
            self.last = time.monotonic()
            if error.code == 404:
                return {"error": 404}
            # A restarted run does not know the hourly quota an earlier run used; the API says
            # when it resets.
            if error.code == 429 and error.headers.get("ratelimit-reset"):
                reset = int(error.headers["ratelimit-reset"])
                print(f"Hourly quota used; waiting {reset} s for it to reset", flush=True)
                time.sleep(reset + 1)
                return self.get(path)
            raise
        if remaining is not None and int(remaining) == 0:
            print(f"Hourly quota used; waiting {reset} s for it to reset", flush=True)
            time.sleep(int(reset) + 1)
        return body


def save(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    with open(temporary, "w", encoding="utf-8") as file:
        file.write(json.dumps(value, ensure_ascii=False, indent=1))
        # On disk before the rename, so a crash cannot leave a zero-filled file under the name.
        file.flush()
        os.fsync(file.fileno())
    temporary.replace(path)


def stored(api, path, endpoint):
    if not path.exists():
        save(path, api.get(endpoint))
    return json.loads(path.read_text(encoding="utf-8"))


def listings(directory, names, chosen=None):
    result = []
    for name in names:
        platform = PLATFORMS[name]
        path = directory / f"{name}-listing.json"
        games = json.loads(path.read_text(encoding="utf-8"))
        if chosen is not None:
            wanted = set(chosen.get(name, []))
            games = [game for game in games if game["id"] in wanted]
            unlisted = wanted - {game["id"] for game in games}
            if unlisted:
                raise SystemExit(f"{name}: games not in the listing: {sorted(unlisted)[:10]}")
        result.append((name, platform, games))
    return result


def details(api, root, platforms):
    for name, platform, games in platforms:
        for number, game in enumerate(games, 1):
            stored(api, root / "games" / f"{game['id']}.json", f"/games/{game['id']}")
            stored(api, root / "releases" / f"{game['id']}-{platform}.json",
                   f"/games/{game['id']}/platforms/{platform}")
            if number % 50 == 0 or number == len(games):
                print(f"details {name}: {number}/{len(games)}", flush=True)


def images(record):
    """Every image URL in a cover or screenshot list."""
    found = []
    for group in record.get("cover_groups", []):
        found += [cover["image"] for cover in group.get("covers", []) if cover.get("image")]
    found += [shot["image"] for shot in record.get("screenshots", []) if shot.get("image")]
    return found


def art(api, root, platforms):
    urls = []
    for name, platform, games in platforms:
        for number, game in enumerate(games, 1):
            for kind in ("covers", "screenshots"):
                urls += images(stored(api, root / kind / f"{game['id']}-{platform}.json",
                                      f"/games/{game['id']}/platforms/{platform}/{kind}"))
            if number % 50 == 0 or number == len(games):
                print(f"art lists {name}: {number}/{len(games)}", flush=True)
    urls = [url for url in dict.fromkeys(urls) if not image_path(root, url).exists()]
    # Images come from MobyGames' CDN, outside the API and its limits.
    with ThreadPoolExecutor(max_workers=8) as pool:
        for number, _ in enumerate(pool.map(lambda url: download(root, url), urls), 1):
            if number % 100 == 0 or number == len(urls):
                print(f"images: {number}/{len(urls)}", flush=True)


def image_path(root, url):
    return root / "images" / urllib.parse.urlsplit(url).path.lstrip("/")


def download(root, url):
    target = image_path(root, url)
    payload, _ = fetch(url)
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_name(target.name + ".tmp")
    temporary.write_bytes(payload)
    temporary.replace(target)


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("passes", nargs="+", choices=("details", "art"))
    parser.add_argument("--platforms", nargs="+", choices=PLATFORMS, required=True)
    parser.add_argument("--root", type=Path, default=Path(".downloads/mobygames"))
    parser.add_argument("--games-from", type=Path)
    args = parser.parse_args()
    api = Api(os.environ["MOBYGAMES_API_KEY"])
    chosen = json.loads(args.games_from.read_text(encoding="utf-8")) if args.games_from else None
    platforms = listings(args.root, args.platforms, chosen)
    for name in args.passes:
        (details if name == "details" else art)(api, args.root, platforms)


if __name__ == "__main__":
    main()
