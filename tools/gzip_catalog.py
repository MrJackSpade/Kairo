#!/usr/bin/env python3
"""Converts an app's plain description files to the gzipped ones catalog_files.py names.

  gzip_catalog.py <app root>

Each catalog/research/descriptions*-v1.json, catalog/research/games-v1.json and catalog/parts/data.json,
data.nsfw.json becomes <name>.gz holding exactly its bytes; the plain file is removed once the .gz is
read back equal. Prints file names only, never content.
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import catalog_files  # noqa: E402


def plain_files(root):
    research = root / "catalog/research"
    yield from sorted(research.glob("descriptions*-v1.json"))
    yield research / "games-v1.json"
    for stem in catalog_files.GZIPPED_PARTS:
        yield root / "catalog/parts" / f"{stem}.json"


def main():
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    root = Path(sys.argv[1]).resolve()
    converted = 0
    for plain in plain_files(root):
        if not plain.is_file():
            continue
        target = plain.with_name(plain.name + ".gz")
        if target.exists():
            raise SystemExit(f"{target} already exists beside {plain.name}")
        content = plain.read_bytes()
        catalog_files.write_bytes(target, content)
        if catalog_files.read_bytes(target) != content:
            raise SystemExit(f"{target.name} does not read back as {plain.name}")
        plain.unlink()
        converted += 1
        print(f"{plain.relative_to(root).as_posix()} -> {target.name}")
    print(f"{root.name}: {converted} files gzipped")


if __name__ == "__main__":
    main()
