#!/usr/bin/env python3
"""Write an app's original game descriptions in batches.

  descriptions.py worklist <app root> <out.jsonl> --mobygames <dump> [--limit N]
      One line per game without a description, with the reference facts for writing one:
      title, aliases, year, developer, publisher, MobyGames genres, and short excerpts of the
      LaunchBox and MobyGames descriptions. The excerpts are reference only; catalog
      descriptions are written fresh and never copy their wording.
  descriptions.py add <app root> <batch.json>
      Merge {"<game key>": "description"} into catalog/research/descriptions-v1.json. Each key
      must be a game in games-v1.json that has no description yet.

Run research.py afterwards to carry the descriptions into games-v1.json.
"""

import argparse
import html
import json
import re
from pathlib import Path

EXCERPT = 360
MAX_LENGTH = 400


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
        if key in described or entry.get("description"):
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
    args = parser.parse_args()
    if args.command == "worklist":
        worklist(args.root.resolve(), args.output, args.mobygames, args.limit)
    else:
        add(args.root.resolve(), args.batch)


if __name__ == "__main__":
    main()
