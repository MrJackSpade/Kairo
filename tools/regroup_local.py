#!/usr/bin/env python3
"""Moves an app's research records from the local games of single disks to the local game of
their set, after research.py began grouping an unmatched multi-disk set into one local game
(disk_sets.group).

Usage: regroup_local.py <app root>

A disk of a set used to be its own local game, keyed by its file name; now the set is one, keyed
by the set's name. Every key and value in catalog/research/*.json naming an old key is renamed to
the new one. When two disks of a set both have a record in one file, the first disk's (by its disk
tag) is kept and the others are reported, so nothing is lost silently.
"""

import argparse
import hashlib
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from disk_sets import DISK_TAG, _order, group  # noqa: E402


def local_key(slug, group_name):
    """As research.local_key."""
    return f"{slug}:local-" + hashlib.sha1(group_name.encode("utf-8")).hexdigest()[:10]


def renames(root, config):
    """{old local key: new local key}, and each new key's old keys in disk order."""
    result = {}
    for set_name, (index_path, slugs) in config.SCANS.items():
        index = json.loads((root / index_path).read_text(encoding="utf-8"))
        for archive in index["archives"]:
            base = archive["path"].rsplit("/", 1)[-1]
            if archive["status"] != "ready" or not DISK_TAG.search(base):
                continue
            old = local_key(slugs[0], f"{set_name}:{base}")
            new = local_key(slugs[0], f"{set_name}:{group(archive['path'])}")
            if old != new:
                result[old] = (new, _order(base))
    return result


def rename(value, moves, dropped, where):
    if isinstance(value, dict):
        out = {}
        claims = {}
        for key in sorted(value, key=lambda k: moves[k][1] if k in moves else (0,)):
            target = moves[key][0] if key in moves else key
            if target in out:
                dropped.append(f"{where}: kept {claims[target]} for {target}, dropped {key}")
                continue
            claims[target] = key
            out[target] = rename(value[key], moves, dropped, where)
        return out
    if isinstance(value, list):
        return [rename(item, moves, dropped, where) for item in value]
    if isinstance(value, str) and value in moves:
        return moves[value][0]
    return value


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("root", type=Path)
    root = parser.parse_args().root.resolve()
    sys.path.insert(0, str(root / "tools"))
    import catalog_config
    moves = renames(root, catalog_config)
    print(f"{len(moves)} disks' local games join their set's")
    dropped = []
    for path in sorted((root / "catalog/research").glob("*.json")):
        text = path.read_text(encoding="utf-8")
        if not any(old in text for old in moves):
            continue
        data = json.loads(text)
        changed = rename(data, moves, dropped, path.name)
        path.write_text(json.dumps(changed, indent=1, ensure_ascii=False, sort_keys=True) + "\n", encoding="utf-8")
        print(f"rewrote {path.name}")
    for line in dropped:
        print("dropped:", line)


if __name__ == "__main__":
    main()
