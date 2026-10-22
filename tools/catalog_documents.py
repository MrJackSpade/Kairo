#!/usr/bin/env python3
"""Assemble an app's reviewed catalog source into its content-ID-keyed documents.

A catalog source is {"schemaVersion": 1, "datasets": [...]}; each dataset names its provenance
and lists game records that carry their media's "contentIds". build() shards those records by
content ID; build_pack() joins them with the name index into one downloadable snapshot. What a
record may contain is the app's business: every function here takes the app's validator, which
raises ValueError on an invalid record and returns it without "contentIds".
"""

import json


def compact(obj):
    return (json.dumps(obj, sort_keys=True, separators=(",", ":"), ensure_ascii=False) + "\n").encode("utf-8")


def require(condition, message):
    if not condition:
        raise ValueError(message)


def inherit(record, shared):
    """A disk record with its game's shared fields filled in."""
    if "game" not in record:
        return record
    require(record["game"] in shared, f"unknown game {record['game']}")
    return {**shared[record["game"]], **{key: value for key, value in record.items() if key != "game"}}


def build(source, validate_record):
    """(manifest, {two-hex-digit prefix: {content ID: record}}) for a catalog source."""
    require(source.get("schemaVersion") == 1, "unsupported source schema")
    datasets = source.get("datasets")
    require(isinstance(datasets, list), "datasets must be an array")
    by_id = {}
    provenance = []
    dataset_ids = set()
    for dataset in datasets:
        require(isinstance(dataset, dict), "dataset must be an object")
        dataset_id = dataset.get("id")
        info = dataset.get("provenance")
        require(isinstance(dataset_id, str) and dataset_id and dataset_id not in dataset_ids,
                "invalid or duplicate dataset ID")
        require(isinstance(info, dict) and all(isinstance(info.get(k), str) and info[k]
                                               for k in ("source", "license", "attribution")),
                "dataset needs source, license and attribution")
        require(isinstance(dataset.get("games"), list), "games must be an array")
        dataset_ids.add(dataset_id)
        provenance.append({"id": dataset_id, **{k: info[k] for k in ("source", "license", "attribution")}})
        for game in dataset["games"]:
            normalized = validate_record(game)
            for content_id in game["contentIds"]:
                require(content_id not in by_id or by_id[content_id] == normalized,
                        f"conflicting identity for {content_id}; review before merging")
                by_id[content_id] = normalized
    shards = {}
    for content_id, metadata in sorted(by_id.items()):
        prefix = content_id.split(":", 1)[1][:2]
        shards.setdefault(prefix, {})[content_id] = metadata
    manifest = {"schemaVersion": 1, "shards": sorted(shards),
                "datasets": sorted(provenance, key=lambda x: x["id"]), "games": len(by_id)}
    return manifest, shards


def build_pack(source, name_index, validate_record, placeholder_id):
    """The downloadable snapshot: every content ID's record plus the name index. placeholder_id
    is any valid content ID of the app, used to validate records that have none of their own."""
    _, shards = build(source, validate_record)
    if name_index.get("schemaVersion") != 1 or not isinstance(name_index.get("games"), dict) or not isinstance(name_index.get("names"), dict):
        raise ValueError("invalid name index")
    for key, record in name_index["games"].items():
        if not isinstance(key, str) or not isinstance(record, dict) or "game" in record:
            raise ValueError("invalid name index record")
        validate_record({**record, "contentIds": [placeholder_id]})
    if any(not isinstance(key, str) or not key.isalnum() or len(key) not in range(4, 129) or
           not isinstance(value, str) or value not in name_index["games"]
           for key, value in name_index["names"].items()):
        raise ValueError("invalid name index alias")
    games = {}
    for shard in shards.values():
        games.update(shard)
    for record in games.values():
        validate_record({**inherit(record, name_index["games"]), "contentIds": [placeholder_id]})
    return {"schemaVersion": 1, "games": games, "nameIndex": name_index}
