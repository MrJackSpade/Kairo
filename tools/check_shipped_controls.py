#!/usr/bin/env python3
"""Fail if anything an app ships sends more than one thing on one press, or leaves a D-pad unset.

Usage: check_shipped_controls.py <app root>

Reads what the app actually ships, not its sources: every document in catalog/core-v4.zip (the
update apps download) and catalog/parts/controls.json. Every controller binding in them must pass
app_catalog.single_output with the app's catalog_config.MODIFIER_KEYS, and every system mapping
(system-v1.json, system-v2.json) must bind all four D-pad directions.
"""

import gzip
import io
import json
import sys
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import app_catalog


def binding_lists(node, path):
    if isinstance(node, dict):
        for key, value in node.items():
            yield from binding_lists(value, path + [key])
    elif isinstance(node, list):
        if node and all(isinstance(item, dict) and "input" in item for item in node):
            yield path, node
        else:
            for index, value in enumerate(node):
                yield from binding_lists(value, path + [str(index)])


def shipped_documents(root):
    with zipfile.ZipFile(root / "catalog/core-v4.zip") as archive:
        for name in archive.namelist():
            raw = archive.read(name)
            if name.endswith(".gz"):
                raw = gzip.decompress(raw)
            if name.endswith((".json", ".json.gz")):
                yield f"core-v4.zip!{name}", json.loads(raw)
    yield "parts/controls.json", json.loads((root / "catalog/parts/controls.json").read_text(encoding="utf-8"))


def main():
    root = Path(sys.argv[1]).resolve()
    config = app_catalog.load_config(root)
    modifiers = frozenset(getattr(config, "MODIFIER_KEYS", ()))
    failures, systems = [], 0
    for name, document in shipped_documents(root):
        for path, bindings in binding_lists(document, []):
            where = f"{name}:{'/'.join(path)}"
            failures += [f"{where}: {b.get('input')} sends more than one thing: {b}"
                         for b in bindings if not app_catalog.single_output(b, modifiers)]
            if path[-2:] == ["controller", "bindings"] and any(part.startswith("system-v") for part in [name, *path]):
                systems += 1
                bound = {b["input"] for b in bindings}
                failures += [f"{where}: the D-pad's {control} is unset" for control in app_catalog.DPAD if control not in bound]
    if not systems:
        failures.append("no system mapping found in what the app ships")
    if failures:
        print("\n".join(failures), file=sys.stderr)
        raise SystemExit(f"{len(failures)} shipped controller problems")
    print(f"shipped controls: {systems} system mappings, every binding one output per press")


if __name__ == "__main__":
    main()
