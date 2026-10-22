#!/usr/bin/env python3
"""Generate an app's catalog from its research file.

Usage: app_catalog.py <app root> [--check]

The app's tools/catalog_config.py names its APP (a catalog_parts.APPS key), its media KINDS and
its ARTWORK references. catalog/research/games-v1.json holds one record per game:

  {"schemaVersion": 1, "games": {"<platform>:<id>": {
      "title": "...", "aliases": [...], "description": "...",   shipped display metadata
      "boxArtUrl": "...", "screenshotUrl": "...",               LaunchBox images, compacted
      "artwork": {...},                                         or a reviewed compact pick
      "contentIds": [...], "sourceGroups": [...],               media this game is known by
      "adult": true,                                            heart marker, core-review exclusion
      "launchbox": "...", "mobygames": 0, "year": 0, ...         research only, never shipped
  }}}

catalog/core-review-v1.json lists excluded games and approved artwork. Outputs: catalog/source-v1.json,
catalog/core-v3.json with its .meta.json, catalog/parts/, the artwork exclusion list and the
importable artwork package. --check regenerates in memory and fails if a committed file differs.
"""

import argparse
import hashlib
import importlib.util
import json
import re
import sys
import unicodedata
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import catalog_documents
from catalog_documents import compact, require
from catalog_package import image_paths
from catalog_parts import generate_parts

GAME_KEY = re.compile(r"[a-z0-9]+:[A-Za-z0-9._-]{1,64}\Z")
SHIPPED = ("title", "description", "aliases")
CORE_VERSION = 3
MAX_IDS = 64


def load_config(root):
    spec = importlib.util.spec_from_file_location("catalog_config", root / "tools/catalog_config.py")
    config = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(config)
    return config


def lookup_name(value):
    """A name's index form: annotations, disk labels and a file extension removed, then NFKC,
    lower-case, letters and digits only."""
    value = re.sub(r"\[[^]]*]", "", value)
    value = re.sub(r"\((?:disk|disc|fd|side)\s*[0-9a-z]{1,3}[^)]*\)", "", value, flags=re.I)
    value = re.sub(r"\.[A-Za-z0-9]{2,4}$", "", value)
    return "".join(char.lower() for char in unicodedata.normalize("NFKC", value) if char.isalnum())


def validator(kinds, artwork):
    content_id = re.compile(r"sha256-(?:" + "|".join(sorted(kinds)) + r")-v1:[0-9a-f]{64}\Z")

    def validate_record(record):
        require(isinstance(record, dict), "game must be an object")
        require(set(record) <= {"game", "title", "description", "aliases", "artwork", "heart",
                                "hidden", "setDisks", "contentIds"}, "unknown game field")
        ids = record.get("contentIds")
        require(isinstance(ids, list) and 1 <= len(ids) <= MAX_IDS and len(ids) == len(set(ids)) and
                all(isinstance(value, str) and content_id.fullmatch(value) for value in ids),
                "contentIds must list 1-64 distinct content IDs")
        require("game" not in record or (isinstance(record["game"], str) and GAME_KEY.fullmatch(record["game"])),
                "invalid game reference")
        title = record.get("title")
        require((title is None and "game" in record) or
                (isinstance(title, str) and 0 < len(title.strip()) <= 256), "invalid title")
        description = record.get("description")
        require(description is None or (isinstance(description, str) and 0 < len(description.strip()) <= 8000),
                "invalid description")
        aliases = record.get("aliases", [])
        require(isinstance(aliases, list) and len(aliases) <= 64 and
                all(isinstance(x, str) and 0 < len(x.strip()) <= 256 for x in aliases), "invalid aliases")
        require("heart" not in record or type(record["heart"]) is bool, "invalid heart marker")
        require("hidden" not in record or type(record["hidden"]) is bool, "invalid hidden flag")
        set_disks = record.get("setDisks", [])
        require(isinstance(set_disks, list) and len(set_disks) <= 32 and
                all(isinstance(x, str) and content_id.fullmatch(x) for x in set_disks), "invalid setDisks")
        artwork.expand(record.get("artwork", {}))
        return {key: value for key, value in record.items() if key != "contentIds"}

    return validate_record


def artwork_group(entry):
    """A game's artwork group, which names its image cache folder: its LaunchBox DatabaseID.
    (Kairo98 numbered groups by public page ID, which has no reliable mapping for older
    DatabaseIDs; reviewed picks carried over from it keep their own group in "artwork".)"""
    return int(entry["launchbox"])


def metadata(key, entry, config):
    record = {field: entry[field] for field in SHIPPED if entry.get(field)}
    if entry.get("adult"):
        record["heart"] = True
    urls = {kind: entry[field] for field, kind in (("boxArtUrl", "boxArt"), ("screenshotUrl", "preview"))
            if entry.get(field)}
    if "artwork" in entry:
        # A reviewed pick, kept exactly as reviewed.
        require(not urls, f"{key}: artwork and image URLs both given")
        config.ARTWORK.expand(entry["artwork"])
        record["artwork"] = entry["artwork"]
    elif urls:
        require("launchbox" in entry, f"{key}: LaunchBox images need the game's LaunchBox DatabaseID")
        record["artwork"] = config.ARTWORK.from_urls(config.ARTWORK_CODE, artwork_group(entry), urls)
    return record


def generate(root, check=False):
    config = load_config(root)
    validate_record = validator(config.KINDS, config.ARTWORK)
    research = json.loads((root / "catalog/research/games-v1.json").read_text(encoding="utf-8"))
    require(research.get("schemaVersion") == 1, "unsupported research schema")
    policy = json.loads((root / "catalog/core-review-v1.json").read_text(encoding="utf-8"))
    games, names, source_games = {}, {}, []
    for key, entry in sorted(research["games"].items()):
        require(GAME_KEY.fullmatch(key) and key.split(":", 1)[0] in config.PLATFORMS, f"invalid game key {key}")
        require(not entry.get("adult") or key in policy["excluded"], f"adult game missing from core review: {key}")
        record = metadata(key, entry, config)
        validate_record({**record, "contentIds": [f"sha256-{sorted(config.KINDS)[0]}-v1:" + "0" * 64]})
        games[key] = record
        ids = sorted(set(entry.get("contentIds", [])))
        for start in range(0, len(ids), MAX_IDS):
            source_games.append({"contentIds": ids[start:start + MAX_IDS], "game": key})
        for candidate in (entry["title"], *entry.get("aliases", []),
                          *(group.rsplit(":", 1)[-1] for group in entry.get("sourceGroups", []))):
            normalized = lookup_name(candidate)
            if 4 <= len(normalized) <= 128:
                names.setdefault(normalized, set()).add(key)
    require(policy["excluded"].keys() <= games.keys(), "core review excludes unknown games")
    unique_names = {name: next(iter(keys)) for name, keys in sorted(names.items()) if len(keys) == 1}
    name_index = {"schemaVersion": 1, "games": games, "names": unique_names}
    source = {"schemaVersion": 1, "datasets": [{"id": f"{config.APP}-research",
        "provenance": {"source": f"{config.APP} research catalog: LaunchBox Games Database and MobyGames references, local media scans",
                       "license": "Original descriptions; titles and image references from LaunchBox; artwork used under fair use",
                       "attribution": "LaunchBox Games Database contributors; MobyGames contributors; original descriptions"},
        "games": source_games}]}
    manifest, shards = catalog_documents.build(source, validate_record)
    documents = {"base-v1.json": {"schemaVersion": 1, "games": {}}, "manifest-v1.json": manifest,
                 "name-index-v1.json": name_index,
                 **{f"shards/{prefix}.json": {"schemaVersion": 1, "games": records}
                    for prefix, records in shards.items()}}
    placeholder = f"sha256-{sorted(config.KINDS)[0]}-v1:" + "0" * 64
    pack = compact({**catalog_documents.build_pack(source, name_index, validate_record, placeholder),
                    "coreVersion": CORE_VERSION})
    outputs = {"catalog/source-v1.json": compact(source), "catalog/core-v3.json": pack,
               "catalog/core-v3.meta.json": (json.dumps({"schemaVersion": 1, "archive": "core-v3.json",
                   "sha256": hashlib.sha256(pack).hexdigest(), "size": len(pack)}, separators=(",", ":")) + "\n").encode("utf-8")}
    for path, content in outputs.items():
        if check:
            require((root / path).read_bytes() == content, f"Stale {path}")
        else:
            (root / path).parent.mkdir(parents=True, exist_ok=True)
            (root / path).write_bytes(content)
    optional = {"schemaVersion": 1, "games": {}, "nameIndex": name_index}
    generate_parts(root, config.APP, documents, optional, config.ARTWORK.expand, config.ARTWORK.compact, check)
    print(f"{config.APP}: {len(games)} games, {manifest['games']} content IDs, {len(unique_names)} unique names, "
          f"{len(image_paths(name_index, config.ARTWORK.expand))} artwork paths")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("root", type=Path)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    generate(args.root.resolve(), args.check)


if __name__ == "__main__":
    main()
