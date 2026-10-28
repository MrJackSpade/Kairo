#!/usr/bin/env python3
"""Write an app's original game descriptions in batches.

  descriptions.py worklist <app root> <out.jsonl> --mobygames <dump> [--limit N]
      One line per game the library shows (one with media, not hidden) without a description,
      with the reference facts for writing one:
      title, aliases, year, developer, publisher, MobyGames genres, and short excerpts of the
      LaunchBox and MobyGames descriptions. The excerpts are reference only; catalog
      descriptions are written fresh and never copy their wording.
  descriptions.py add <app root> <batch.json>
      Merge {"<game key>": "description"} into catalog/research/descriptions-v1.json. Each key
      must be a game in games-v1.json that has no description yet.

  descriptions.py pending <app root> <language> <out.json> [--adult] [--limit N]
      Up to N of the English descriptions (or with --adult, the frank adult descriptions) not yet
      translated into <language> (catalog_languages.LANGUAGES), as {"<game key>": "English"}.
  descriptions.py translate <app root> <language> <batch.json> [--adult]
      Merge {"<game key>": "translation"} into catalog/research/descriptions[-adult]-<language>-v1.json.
      Each key must have that English description and no translation yet.

Run research.py afterwards to carry the descriptions into games-v1.json.
"""

import argparse
import html
import json
import re
from pathlib import Path

EXCERPT = 360
MAX_LENGTH = 400
# The longest description the app shows (LayeredGameCatalog.validDescription).
MAX_TRANSLATION = 8000


def read(path, default=None):
    return json.loads(path.read_text(encoding="utf-8")) if path.is_file() else default


def plain(text):
    text = re.sub(r"<[^>]+>", " ", text or "")
    return re.sub(r"\s+", " ", html.unescape(text)).strip()


def excerpt(text):
    text = plain(text)
    return text if len(text) <= EXCERPT else text[:EXCERPT].rsplit(" ", 1)[0] + " …"


def worklist(root, output, moby_root, limit):
    games = read(root / "catalog/research/games-v1.json")["games"]
    described = read(root / "catalog/research/descriptions-v1.json", {"descriptions": {}})["descriptions"]
    snapshots = {}
    for path in (root / ".downloads/launchbox").glob("*-snapshot.json"):
        snapshots.update(read(path))
    listings = {}
    for path in moby_root.glob("*-listing.json"):
        for game in read(path):
            listings[game["id"]] = game
    lines = []
    for key, entry in sorted(games.items()):
        # Only games the library can show: a game no medium identifies, or a hidden one, is never seen.
        if key in described or entry.get("description") or not entry.get("contentIds") or entry.get("hidden"):
            continue
        item = {"key": key, "title": entry["title"]}
        for field in ("aliases", "year", "developer", "publisher", "adult"):
            if entry.get(field):
                item[field] = entry[field]
        if entry.get("sourceGroups") and ":local-" in key:
            item["files"] = entry["sourceGroups"][:3]
        launchbox = snapshots.get(entry.get("launchbox", ""), {})
        if launchbox.get("Overview"):
            item["launchbox"] = excerpt(launchbox["Overview"])
        moby_id = entry.get("mobygames")
        if moby_id:
            item["genres"] = listings.get(moby_id, {}).get("genres", [])
            record = read(moby_root / "games" / f"{moby_id}.json", {})
            if record.get("description"):
                item["mobygames"] = excerpt(record["description"])
        lines.append(json.dumps(item, ensure_ascii=False))
        if limit and len(lines) >= limit:
            break
    output.write_text("\n".join(lines) + ("\n" if lines else ""), encoding="utf-8")
    print(f"{len(lines)} games to describe -> {output}")


def add(root, batch_path):
    games = read(root / "catalog/research/games-v1.json")["games"]
    path = root / "catalog/research/descriptions-v1.json"
    document = read(path, {"schemaVersion": 1, "descriptions": {}})
    batch = read(batch_path)
    for key, text in batch.items():
        if key not in games:
            raise SystemExit(f"unknown game {key}")
        if key in document["descriptions"]:
            raise SystemExit(f"{key} already has a description")
        if not isinstance(text, str) or not 0 < len(text.strip()) <= MAX_LENGTH or text != text.strip():
            raise SystemExit(f"{key}: description must be 1-{MAX_LENGTH} characters without outer spaces")
    document["descriptions"].update(batch)
    document["descriptions"] = dict(sorted(document["descriptions"].items()))
    path.write_text(json.dumps(document, ensure_ascii=False, indent=1, sort_keys=True) + "\n", encoding="utf-8")
    print(f"{len(batch)} added; {len(document['descriptions'])} of {len(games)} games described")


def translation_paths(root, language, adult):
    from catalog_languages import LANGUAGES
    if language not in LANGUAGES:
        raise SystemExit(f"{language} is not one of {', '.join(LANGUAGES)}")
    kind = "-adult" if adult else ""
    research = root / "catalog/research"
    return research / f"descriptions{kind}-v1.json", research / f"descriptions{kind}-{language}-v1.json"


def pending(root, language, output, adult, limit):
    source, target = translation_paths(root, language, adult)
    english = read(source, {"descriptions": {}})["descriptions"]
    done = read(target, {"descriptions": {}})["descriptions"]
    left = {key: text for key, text in sorted(english.items()) if key not in done}
    batch = dict(list(left.items())[:limit] if limit else left.items())
    output.write_text(json.dumps(batch, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"{len(batch)} of {len(left)} untranslated ({len(done)} of {len(english)} done) -> {output}")


def translate(root, language, batch_path, adult):
    source, target = translation_paths(root, language, adult)
    english = read(source, {"descriptions": {}})["descriptions"]
    document = read(target, {"schemaVersion": 1, "descriptions": {}})
    batch = read(batch_path)
    for key, text in batch.items():
        if key not in english:
            raise SystemExit(f"{key} has no English description in {source.name}")
        if key in document["descriptions"]:
            raise SystemExit(f"{key} is already translated in {target.name}")
        if not isinstance(text, str) or not 0 < len(text.strip()) <= MAX_TRANSLATION or text != text.strip():
            raise SystemExit(f"{key}: a translation must be 1-{MAX_TRANSLATION} characters without outer spaces")
    document["descriptions"].update(batch)
    document["descriptions"] = dict(sorted(document["descriptions"].items()))
    target.write_text(json.dumps(document, ensure_ascii=False, indent=1, sort_keys=True) + "\n", encoding="utf-8")
    print(f"{len(batch)} added; {len(document['descriptions'])} of {len(english)} translated into {language}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command", required=True)
    work = commands.add_parser("worklist")
    work.add_argument("root", type=Path)
    work.add_argument("output", type=Path)
    work.add_argument("--mobygames", type=Path, required=True)
    work.add_argument("--limit", type=int)
    merge = commands.add_parser("add")
    merge.add_argument("root", type=Path)
    merge.add_argument("batch", type=Path)
    todo = commands.add_parser("pending")
    todo.add_argument("root", type=Path)
    todo.add_argument("language")
    todo.add_argument("output", type=Path)
    todo.add_argument("--adult", action="store_true")
    todo.add_argument("--limit", type=int)
    done = commands.add_parser("translate")
    done.add_argument("root", type=Path)
    done.add_argument("language")
    done.add_argument("batch", type=Path)
    done.add_argument("--adult", action="store_true")
    args = parser.parse_args()
    if args.command == "worklist":
        worklist(args.root.resolve(), args.output, args.mobygames, args.limit)
    elif args.command == "add":
        add(args.root.resolve(), args.batch)
    elif args.command == "pending":
        pending(args.root.resolve(), args.language, args.output, args.adult, args.limit)
    else:
        translate(args.root.resolve(), args.language, args.batch, args.adult)


if __name__ == "__main__":
    main()
