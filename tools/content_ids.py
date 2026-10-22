#!/usr/bin/env python3
"""Content IDs of game media in a folder of images and ZIPs, without extracting anything.

A content ID is "sha256-<kind>-v1:" followed by the SHA-256 of the medium's bytes as stored
(inside a ZIP: the entry's uncompressed bytes). Each app names its kinds and which files carry
them through a Media description, so the same scan serves every platform.
"""

import hashlib
import re
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Optional

CHUNK = 1024 * 1024
ANNOTATION = re.compile(r"\s*\[[^]]*\]")


@dataclass(frozen=True)
class Media:
    """An app's media rules: the kind of a file name (None when it is not game media), the
    member whose bytes a kind's ID hashes (a CD descriptor names its track data), and each
    kind's size limit. version changes when the rules do, so cached scans are redone."""
    kind: Callable[[str], Optional[str]]
    data: Callable[[str, str], str]
    limit: Callable[[str], int]
    version: int


def same_file(name, kind):
    return name


def title_hint(name):
    return ANNOTATION.sub("", Path(name).stem).strip(" -_")


def valid_entry(info):
    name = info.filename.replace("\\", "/")
    parts = name.split("/")
    return (not info.is_dir() and not name.startswith("/") and
            all(part not in ("", ".", "..") for part in parts) and
            not re.match(r"^[a-zA-Z]:", name))


def digest(stream, kind):
    sha = hashlib.sha256()
    while block := stream.read(CHUNK):
        sha.update(block)
    return f"sha256-{kind}-v1:" + sha.hexdigest()


def scan(root, media, previous=None):
    """Scan direct media files and ordinary ZIPs; nested ZIPs are queued for later. A previous
    scan's IDs are reused for files whose size and modification time are unchanged."""
    root = Path(root).resolve()
    old = {item["path"]: item for item in (previous or {}).get("archives", [])}
    formats_current = (previous or {}).get("mediaFormatsVersion", 1) >= media.version
    results = []
    for path in sorted(root.rglob("*")):
        if not path.is_file() or (path.suffix.lower() != ".zip" and not media.kind(path.name)):
            continue
        relative = path.relative_to(root).as_posix()
        stat = path.stat()
        fingerprint = {"size": stat.st_size, "mtimeNs": stat.st_mtime_ns}
        cached = old.get(relative)
        unchanged = cached and cached.get("fingerprint") == fingerprint
        if unchanged and formats_current:
            results.append(cached)
            continue
        cached_disks = {disk["entry"]: disk["contentId"] for disk in cached.get("disks", [])} if unchanged else {}
        item = {"path": relative, "titleHint": title_hint(path.name),
                "fingerprint": fingerprint, "disks": [], "status": "pending"}
        try:
            if path.suffix.lower() != ".zip":
                kind = media.kind(path.name)
                data = path.with_name(media.data(path.name, kind))
                if not data.is_file():
                    raise ValueError(f"{data.name} is missing")
                size = data.stat().st_size
                if size <= 0 or size > media.limit(kind):
                    raise ValueError("disk image exceeds size limit")
                with data.open("rb") as source:
                    content_id = cached_disks.get(None) or digest(source, kind)
                    item["disks"].append({"entry": None, "contentId": content_id})
            else:
                with zipfile.ZipFile(path) as archive:
                    entries = archive.infolist()
                    members = {info.filename.lower(): info for info in entries}
                    for info in entries:
                        if not valid_entry(info):
                            raise ValueError("unsafe ZIP entry")
                        if info.filename.lower().endswith(".zip"):
                            item["nestedZip"] = True
                        kind = media.kind(info.filename)
                        if not kind:
                            continue
                        data_name = media.data(info.filename, kind)
                        data = info if data_name == info.filename else members.get(data_name.lower())
                        if data is None:
                            raise ValueError(f"{data_name} is missing")
                        if (data.file_size <= 0 or data.file_size > media.limit(kind) or
                                data.compress_size <= 0 or data.file_size // data.compress_size > 1000):
                            raise ValueError("disk image exceeds size or expansion limit")
                        content_id = cached_disks.get(info.filename)
                        if not content_id:
                            with archive.open(data) as source:
                                content_id = digest(source, kind)
                        item["disks"].append({"entry": info.filename, "contentId": content_id})
            item["status"] = "ready" if item["disks"] else ("nested-zip" if item.get("nestedZip") else "no-supported-image")
        except (OSError, ValueError, zipfile.BadZipFile, RuntimeError) as error:
            item["status"] = "error"
            item["error"] = str(error)
        results.append(item)
    return {"schemaVersion": 1, "mediaFormatsVersion": media.version,
            "root": str(root), "archives": results}
