#!/usr/bin/env python3
"""Hash an app's game media in a folder of files and archives into a scan index, by the app's
catalog configuration (tools/catalog_config.py: MEDIA and content_ids).

Usage: scan_media.py <app root> <media folder> <index.json>
An existing index is reused for files whose size and modification time are unchanged.
"""
import json
import sys
from collections import Counter
from pathlib import Path


def main():
    if len(sys.argv) != 4:
        raise SystemExit(__doc__)
    app, root, index = Path(sys.argv[1]).resolve(), Path(sys.argv[2]), Path(sys.argv[3])
    sys.path.insert(0, str(app / "tools"))
    from catalog_config import MEDIA, content_ids
    previous = json.loads(index.read_text(encoding="utf-8")) if index.is_file() else None
    result = content_ids.scan(root, MEDIA, previous)
    index.parent.mkdir(parents=True, exist_ok=True)
    temporary = index.with_name(index.name + ".tmp")
    temporary.write_text(json.dumps(result, ensure_ascii=False, indent=1, sort_keys=True) + "\n", encoding="utf-8")
    temporary.replace(index)
    print(json.dumps(dict(Counter(item["status"] for item in result["archives"]))))


if __name__ == "__main__":
    main()
