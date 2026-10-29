#!/usr/bin/env python3
"""Record the Japanese titles games were released under, for Kairo#61.

Usage: japanese_titles.py <app root> --launchbox <Metadata.zip> [--refresh-vndb]

A catalog shows a game's own title in the device's language (research "titles", app_catalog.py).
This finds the Japanese one from known sources only; it never translates or invents one:

  1. VNDB, the game's original title, when exactly one of the platform's VNDB entries carries one of
     the game's names (its title or an alias, as letters and digits) and their years agree.
  2. LaunchBox, an alternate name written in Japanese and given for Japan (one with no region, or
     another, is too often a garbled or wrong name to trust).
  3. The game's own aliases written in Japanese, apart from LaunchBox names it gives for another
     region or none (the same names step 2 leaves out).

Where a source gives several, the one with kanji, then the longest, is taken; a game with none keeps
its current title. Decisions go to catalog/research/titles-v1.json,
{"schemaVersion": 1, "games": {"<game key>": {"ja": "...", "source": "vndb:v123" | "launchbox:123" |
"alias"}}}, which research.py applies to games-v1.json. A recorded decision is kept on later runs, so
a reviewed correction stays; one reviewed as wrong with no known replacement is kept as
{"ja": null, "source": "...", "rejected": "<why>"} and gives the game no Japanese title. Delete a
decision to decide it again. VNDB's platform lists are cached in
.downloads/vndb/<platform>.json; --refresh-vndb fetches them again.
"""

import argparse
import json
import re
import sys
import unicodedata
import urllib.request
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

# A research key's platform -> VNDB's platform code.
VNDB_PLATFORMS = {"pc98": "p98", "pc88": "p88", "x68000": "x68", "msx": "msx", "msx2": "msx", "fmtowns": "fmt"}
VNDB_API = "https://api.vndb.org/kana/vn"
KANA_KANJI = re.compile(r"[ぁ-ゖァ-ヺ㐀-䶿一-鿿ｦ-ﾝ]")
KANJI = re.compile(r"[㐀-䶿一-鿿]")


def japanese(text):
    """Written in Japanese: it has kana or kanji (not only a "・" or full-width Latin)."""
    return isinstance(text, str) and bool(KANA_KANJI.search(text))


def key(name):
    """A name as letters and digits, for comparing romanized titles."""
    return "".join(char.lower() for char in unicodedata.normalize("NFKC", name) if char.isalnum())


def pick(names):
    """The one to show of several Japanese names: one with kanji, then the longest, then by text."""
    return sorted(names, key=lambda name: (not KANJI.search(name), -len(name), name))[0]


def year(text):
    match = re.match(r"(\d{4})", text or "")
    return int(match.group(1)) if match else None


def vndb_entries(platform, cache, refresh):
    """Every VNDB entry on [platform]: id, released, its names, and its Japanese title."""
    if cache.exists() and not refresh:
        return json.loads(cache.read_text(encoding="utf-8"))
    entries, page = [], 1
    while True:
        body = json.dumps({"filters": ["platform", "=", platform], "results": 100, "page": page,
                           "fields": "title,alttitle,released,aliases,titles.lang,titles.title,titles.latin,"
                                     "titles.official,titles.main,olang"}).encode()
        request = urllib.request.Request(VNDB_API, body, {"Content-Type": "application/json"})
        with urllib.request.urlopen(request) as response:
            result = json.load(response)
        entries += result["results"]
        if not result["more"]:
            break
        page += 1
    cache.parent.mkdir(parents=True, exist_ok=True)
    cache.write_text(json.dumps(entries, ensure_ascii=False, indent=1) + "\n", encoding="utf-8", newline="\n")
    return entries


def vndb_index(entries):
    """{name key: [entry]} over each entry's titles, romanizations and aliases, and each entry's
    Japanese title: its official Japanese title, else its original title when it is Japanese."""
    index = {}
    for entry in entries:
        japanese_titles = [t["title"] for t in entry.get("titles", []) if t["lang"] == "ja" and t.get("official")]
        title = pick([t for t in japanese_titles if japanese(t)]) if any(japanese(t) for t in japanese_titles) else (
            entry["alttitle"] if japanese(entry.get("alttitle")) and entry.get("olang") == "ja" else None)
        entry["japanese"] = title
        names = {entry["title"], entry.get("alttitle") or ""}
        for t in entry.get("titles", []):
            names |= {t.get("title") or "", t.get("latin") or ""}
        names |= set((entry.get("aliases") or []))
        for name in names:
            if name and key(name):
                index.setdefault(key(name), [])
                if entry not in index[key(name)]:
                    index[key(name)].append(entry)
    return index


def launchbox_names(metadata, ids):
    """{LaunchBox ID: [(alternate name, region)]} for [ids]."""
    names = {}
    with zipfile.ZipFile(metadata) as archive, archive.open("Metadata.xml") as stream:
        for _, node in ET.iterparse(stream):
            if node.tag == "GameAlternateName":
                game_id = node.findtext("DatabaseID")
                if game_id in ids:
                    names.setdefault(game_id, []).append((node.findtext("AlternateName") or "", node.findtext("Region") or ""))
                node.clear()
            elif node.tag in ("Game", "GameImage", "Platform", "PlatformAlternateName", "Emulator", "EmulatorPlatform"):
                node.clear()
    return names


def decide(game_key, game, vndb, launchbox):
    """The game's Japanese title and its source, or None."""
    if japanese(game.get("title")):
        return None
    names = [game["title"], *game.get("aliases", [])]
    matches = []
    for name in names:
        for entry in vndb.get(key(name), []) if key(name) else []:
            if entry not in matches:
                matches.append(entry)
    matches = [entry for entry in matches
               if game.get("year") is None or year(entry.get("released")) is None or abs(game["year"] - year(entry["released"])) <= 1]
    if len(matches) == 1 and matches[0]["japanese"]:
        return matches[0]["japanese"], f"vndb:{matches[0]['id']}"
    alternates = launchbox.get(str(game.get("launchbox")), [])
    found = [name.strip() for name, region in alternates if japanese(name) and region == "Japan"]
    if found:
        return pick(found), f"launchbox:{game['launchbox']}"
    untrusted = {name for name, region in alternates if region != "Japan"}
    aliases = [alias.strip() for alias in game.get("aliases", []) if japanese(alias) and alias not in untrusted]
    if aliases:
        return pick(aliases), "alias"
    return None


def apply(research, games):
    """Each game's Japanese title from [research]'s titles-v1.json, as its "titles" in games-v1.json."""
    path = Path(research) / "titles-v1.json"
    if not path.exists():
        return
    document = json.loads(path.read_text(encoding="utf-8"))
    if document.get("schemaVersion") != 1:
        raise SystemExit(f"{path}: schemaVersion is not 1")
    for key, value in document["games"].items():
        if key not in games or not (japanese(value.get("ja")) or (value.get("ja") is None and value.get("rejected"))):
            raise SystemExit(f"{path}: invalid title decision for {key}")
        if value["ja"] is not None:
            games[key]["titles"] = {"ja": value["ja"]}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("root", type=Path)
    parser.add_argument("--launchbox", type=Path, required=True)
    parser.add_argument("--refresh-vndb", action="store_true")
    args = parser.parse_args()
    research = args.root / "catalog/research"
    games = json.loads((research / "games-v1.json").read_text(encoding="utf-8"))["games"]
    platforms = {game_key.split(":", 1)[0] for game_key in games}
    vndb = {}
    for platform in sorted({VNDB_PLATFORMS[p] for p in platforms if p in VNDB_PLATFORMS}):
        for name, entries in vndb_index(vndb_entries(platform, args.root / f".downloads/vndb/{platform}.json",
                                                     args.refresh_vndb)).items():
            vndb.setdefault(name, []).extend(e for e in entries if e not in vndb.get(name, []))
    launchbox = launchbox_names(args.launchbox, {str(g["launchbox"]) for g in games.values() if g.get("launchbox")})
    path = research / "titles-v1.json"
    decisions = json.loads(path.read_text(encoding="utf-8"))["games"] if path.exists() else {}
    added = {}
    for game_key, game in sorted(games.items()):
        if game_key in decisions:
            continue
        found = decide(game_key, game, vndb, launchbox)
        if found:
            added[game_key] = {"ja": found[0], "source": found[1]}
    decisions = {k: v for k, v in decisions.items() if k in games} | added
    path.write_text(json.dumps({"schemaVersion": 1, "games": dict(sorted(decisions.items()))}, ensure_ascii=False, indent=2)
                    + "\n", encoding="utf-8", newline="\n")
    sources = {}
    for value in decisions.values():
        sources[value["source"].split(":")[0]] = sources.get(value["source"].split(":")[0], 0) + 1
    print(f"{args.root.name}: {len(decisions)} Japanese titles ({len(added)} new): {sources}")


if __name__ == "__main__":
    sys.exit(main())
