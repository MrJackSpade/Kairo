#!/usr/bin/env python3
"""Fails when a disk in games' sets is listed as one game instead of as shared media (Kairo#109).

KairoTowns#83: a blank formatted floppy several FM Towns sets ship (Twin Cobra, The Secret of Monkey
Island, Tenshouki) was listed as Twin Cobra's, so it named the wrong game in another game's archive.

    python3 shared/tools/audit_shared_media.py <app root>

It reads the app's generated catalog data parts (catalog/parts/data*.json.gz) and fails when
  - a content ID in more than one game's set (setDisks) has a record naming a game: shared media
    has no game of its own, and each set's entry stands for it;
  - a content ID in a game's set has a record naming another game: it is a separate library game.
"""
import gzip
import json
import sys
from pathlib import Path


def records(root):
    """Every disk record of the app's catalog parts, by content ID."""
    found = {}
    for part in sorted((Path(root) / "catalog" / "parts").glob("data*.json.gz")):
        documents = json.loads(gzip.decompress(part.read_bytes()).decode("utf-8"))["documents"]
        for path, document in documents.items():
            if path == "name-index-v1.json" or not isinstance(document, dict):
                continue
            games = document.get("games")
            if isinstance(games, dict):
                found.update({content_id: record for content_id, record in games.items()
                              if ":" in content_id and isinstance(record, dict)})
    return found


def problems(found):
    """What [found] (records by content ID) lists as one game that is shared or another game's."""
    holders = {}
    for content_id, record in found.items():
        for disk in record.get("setDisks", []):
            holders.setdefault(disk, set()).add(record.get("game") or content_id)
    out = []
    for disk, games in sorted(holders.items()):
        own = found.get(disk, {}).get("game")
        if own is None:
            continue
        if len(games) > 1:
            out.append(f"{disk} is in the sets of {', '.join(sorted(games))} but is listed as {own}; "
                       f"shared media names no game")
        elif own not in games:
            out.append(f"{disk} is in the set of {next(iter(games))} but is listed as the separate game {own}")
    return out


def main(argv):
    if len(argv) != 2:
        raise SystemExit("usage: audit_shared_media.py <app root>")
    found = records(argv[1])
    for problem in problems(found):
        print(problem, file=sys.stderr)
    return 1 if problems(found) else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
