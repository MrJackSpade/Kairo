#!/usr/bin/env python3
"""Hash an app's game media in a folder of files and archives into a scan index, by the app's
catalog configuration (tools/catalog_config.py: MEDIA and content_ids).

Usage: scan_media.py <app root> <media folder> <index.json> [--top-level]
An existing index is reused for files whose size and modification time are unchanged.
--top-level scans only the folder's own files, not its subfolders.
"""
import argparse
import json
import sys
from collections import Counter
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("app", type=Path)
    parser.add_argument("root", type=Path)
    parser.add_argument("index", type=Path)
    parser.add_argument("--top-level", action="store_true")
    args = parser.parse_args()
    app, root, index = args.app.resolve(), args.root, args.index
    sys.path.insert(0, str(app / "tools"))
    from catalog_config import MEDIA, content_ids
    previous = json.loads(index.read_text(encoding="utf-8")) if index.is_file() else None
    result = content_ids.scan(root, MEDIA, previous, args.top_level)
    index.parent.mkdir(parents=True, exist_ok=True)
    temporary = index.with_name(index.name + ".tmp")
    temporary.write_text(json.dumps(result, ensure_ascii=False, indent=1, sort_keys=True) + "\n", encoding="utf-8")
    temporary.replace(index)
    print(json.dumps(dict(Counter(item["status"] for item in result["archives"]))))


if __name__ == "__main__":
    main()
