#!/usr/bin/env python3
"""VNDB's descriptions of an app's catalog games, as reference text for writing their own.

  vndb_references.py <app root> <out.json.gz> [--refresh-vndb]

Matches each game the library shows to the app's platform's VNDB entries (japanese_titles.py's
cached list) by name: exactly one entry carries one of the game's names (its title or an alias, as
letters and digits; a name's part before a subtitle counts too, as VNDB writes "Title ~Subtitle~")
and their years agree. Writes {"<game key>": VNDB's description} for descriptions.py worklist
--references and fireworks_describe.py --references: reference only, which a catalog description
never copies. Spoilers and markup are left out.

VNDB also says which entries had an 18+ release on the platform: the games matched to one that the
catalog does not mark adult are printed by key, for the app's overrides-v1.json. Nothing prints a
description's text.
"""

import argparse
import importlib.util
import json
import re
import urllib.request
from pathlib import Path

import catalog_files
from japanese_titles import VNDB_API, VNDB_PLATFORMS, key, vndb_entries, year

RELEASE_API = "https://api.vndb.org/kana/release"
# "Title ~Subtitle~", "Title: Subtitle", "Title - Subtitle".
SUBTITLE = re.compile(r"\s*(?:~|:|\s-\s).*$")


def config_of(root):
    spec = importlib.util.spec_from_file_location("catalog_config", root / "tools/catalog_config.py")
    config = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(config)
    return config


def index(entries):
    """{name key: [entry]} over each entry's names and their parts before a subtitle."""
    found = {}
    for entry in entries:
        names = {entry["title"], entry.get("alttitle") or ""} | set(entry.get("aliases") or [])
        for title in entry.get("titles", []):
            names |= {title.get("title") or "", title.get("latin") or ""}
        for name in names | {SUBTITLE.sub("", name) for name in names}:
            if name and key(name):
                bucket = found.setdefault(key(name), [])
                if entry not in bucket:
                    bucket.append(entry)
    return found


def match(game, names_index):
    names = [game["title"], *game.get("aliases", [])]
    matches = []
    for name in names + [SUBTITLE.sub("", name) for name in names]:
        for entry in names_index.get(key(name), []) if key(name) else []:
            if entry not in matches:
                matches.append(entry)
    matches = [entry for entry in matches if game.get("year") is None or year(entry.get("released")) is None
               or abs(game["year"] - year(entry["released"])) <= 1]
    return matches[0] if len(matches) == 1 else None


def query(url, filters, fields):
    results, page = [], 1
    while True:
        body = json.dumps({"filters": filters, "fields": fields, "results": 100, "page": page}).encode()
        request = urllib.request.Request(url, body, {"Content-Type": "application/json"})
        with urllib.request.urlopen(request) as response:
            answer = json.load(response)
        results += answer["results"]
        if not answer["more"]:
            return results
        page += 1


def plain(text):
    """VNDB's description without spoilers, its source note or markup."""
    text = re.sub(r"\[spoiler\].*?\[/spoiler\]", "", text or "", flags=re.S | re.I)
    text = re.sub(r"\[url=[^\]]*\](.*?)\[/url\]", r"\1", text, flags=re.S | re.I)
    text = re.sub(r"\[/?[a-z]+[^\]]*\]", "", text, flags=re.I)
    text = re.sub(r"\((?:Source|From)[^)]*\)\s*$", "", text.strip(), flags=re.I)
    return re.sub(r"\s+", " ", text).strip()


def chunks(items, size):
    return [items[start:start + size] for start in range(0, len(items), size)]


def references(root, output, refresh):
    config = config_of(root)
    platform = VNDB_PLATFORMS[config.PLATFORMS[0]]
    entries = vndb_entries(platform, root / f".downloads/vndb/{platform}.json", refresh)
    games = catalog_files.read(root / "catalog/research/games-v1.json.gz")["games"]
    names_index = index(entries)
    matched = {}
    for game_key, game in sorted(games.items()):
        if game.get("contentIds") and not game.get("hidden"):
            entry = match(game, names_index)
            if entry:
                matched[game_key] = entry["id"]
    ids = sorted(set(matched.values()))
    texts = {}
    for part in chunks(ids, 50):
        for found in query(VNDB_API, ["or", *[["id", "=", vn] for vn in part]], "description"):
            if plain(found.get("description")):
                texts[found["id"]] = plain(found["description"])
    adult_ids = set()
    for part in chunks(ids, 50):
        filters = ["and", ["platform", "=", platform], ["or", *[["vn", "=", ["id", "=", vn]] for vn in part]]]
        for release in query(RELEASE_API, filters, "minage,vns.id"):
            if (release.get("minage") or 0) >= 18:
                adult_ids |= {vn["id"] for vn in release["vns"]}
    result = {game_key: texts[vn] for game_key, vn in matched.items() if vn in texts}
    catalog_files.write_text(output, json.dumps(result, ensure_ascii=False, indent=1) + "\n")
    unmarked = sorted(game_key for game_key, vn in matched.items() if vn in adult_ids and not games[game_key].get("adult"))
    print(f"{len(matched)} games matched to VNDB, {len(result)} with a description -> {output}")
    print(f"{len(unmarked)} matched games had an 18+ release but are not marked adult:")
    for game_key in unmarked:
        print(f"  {game_key}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("root", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--refresh-vndb", action="store_true")
    args = parser.parse_args()
    if not args.output.name.endswith(".gz"):
        raise SystemExit(f"{args.output} holds descriptions: name it .gz (catalog_files.py)")
    references(args.root.resolve(), args.output, args.refresh_vndb)


if __name__ == "__main__":
    main()
