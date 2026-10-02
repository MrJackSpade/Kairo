#!/usr/bin/env python3
"""Build a deterministic data-only catalog archive and its independent update manifest."""
import hashlib
import json
import zipfile
from pathlib import Path


def compact(value):
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"), sort_keys=True).encode("utf8")


def write_zip(path, files, stored=False):
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, content in sorted(files.items()):
            info = zipfile.ZipInfo(name, (2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_STORED if stored else zipfile.ZIP_DEFLATED
            archive.writestr(info, content)


def build_package(data, images, artwork_root, output, *, product, identity, name, revision, source, archive_url, stored=False):
    files = {"data.json": compact(data)}
    for path in sorted(images):
        assert path.startswith("art/catalog/") and ".." not in path
        files[path] = (artwork_root / path).read_bytes()
    inventory = {path: {"size": len(content), "sha256": hashlib.sha256(content).hexdigest()}
                 for path, content in files.items()}
    files["catalog.json"] = compact({"schemaVersion": 1, "id": identity, "product": product,
        "name": name, "revision": revision, "updateManifest": source, "files": inventory})
    # WebP is already compressed. Stored entries make release downloads byte-for-byte
    # reproducible across the Windows generator and Linux CI zlib versions.
    write_zip(output, files, stored=stored)
    checksum = hashlib.sha256(output.read_bytes()).hexdigest()
    metadata = {"schemaVersion": 1, "id": identity, "product": product, "revision": revision,
                "archive": archive_url, "size": output.stat().st_size, "sha256": checksum}
    output.with_suffix(".meta.json").write_bytes(compact(metadata) + b"\n")
    return metadata


def image_paths(value, expand):
    result = set()
    if isinstance(value, dict):
        for key, child in value.items():
            if key == "artwork":
                art = expand(child)
                result.update(art[kind] for kind in ("boxArt", "preview") if art.get(kind))
            else:
                result.update(image_paths(child, expand))
    elif isinstance(value, list):
        for child in value:
            result.update(image_paths(child, expand))
    return result


def filter_artwork(value, approved, expand, compact_art):
    if isinstance(value, dict):
        result = {}
        for key, child in value.items():
            if key == "artwork":
                art = expand(child)
                selected = {}
                for kind in ("boxArt", "preview"):
                    if art.get(kind) in approved:
                        selected[kind] = art[kind]
                        if kind + "Url" in art:
                            selected[kind + "Url"] = art[kind + "Url"]
                if selected:
                    result[key] = compact_art(selected)
            else:
                result[key] = filter_artwork(child, approved, expand, compact_art)
        return result
    if isinstance(value, list):
        return [filter_artwork(x, approved, expand, compact_art) for x in value]
    return value
