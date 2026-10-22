#!/usr/bin/env python3
"""Content IDs of game media in a folder of images and ZIPs, without extracting anything.

A content ID is "sha256-<kind>-v1:" followed by the SHA-256 of the medium's bytes as stored
(inside a ZIP: the entry's uncompressed bytes). Each app names its kinds and which files carry
them through a Media description, so the same scan serves every platform.
"""

import hashlib
import re
import shutil
import subprocess
import zipfile
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable, Optional

CHUNK = 1024 * 1024
ANNOTATION = re.compile(r"\s*\[[^]]*\]")


@dataclass(frozen=True)
class Media:
    """An app's media rules: the kind of a file name (None when it is not game media), the
    member whose bytes a kind's ID hashes (a CD descriptor names its track data), and each
    kind's size limit. version changes when the rules do, so cached scans are redone.
    seven_zip also opens .7z archives, through 7-Zip's command line.

    candidates name, by a descriptor's suffix, the suffixes its track data may have beside it;
    the first one present is hashed. superseded_by names, by suffix, the suffixes of files that
    replace it when one sits beside it under the same name, so one disc is listed once. Both
    match the shared library's DiskImageLibrary.Format data and supersededBy."""
    kind: Callable[[str], Optional[str]]
    data: Callable[[str, str], str]
    limit: Callable[[str], int]
    version: int
    seven_zip: bool = False
    candidates: dict = field(default_factory=dict)
    superseded_by: dict = field(default_factory=dict)

    def data_for(self, name, kind, present):
        """The member whose bytes name's ID hashes, from present (lower-case name to name)."""
        suffix = Path(name).suffix.lower()
        if suffix not in self.candidates:
            return self.data(name, kind)
        base = name[:-len(suffix)]
        for data_suffix in self.candidates[suffix]:
            found = present.get((base + data_suffix).lower())
            if found:
                return found
        raise ValueError(f"the track data of {Path(name).name} is missing")

    def superseded(self, name, present):
        suffix = Path(name).suffix.lower()
        base = name[:-len(suffix)] if suffix else name
        return any((base + other).lower() in present for other in self.superseded_by.get(suffix, ()))


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


def seven_zip_program():
    found = shutil.which("7z") or shutil.which("7z.exe")
    if found:
        return found
    default = Path("C:/Program Files/7-Zip/7z.exe")
    if default.is_file():
        return str(default)
    raise RuntimeError("7-Zip's 7z command is required to read .7z archives")


def seven_zip_entries(path):
    """[(name, size)] of a .7z archive's files, from 7-Zip's technical listing."""
    listing = subprocess.run([seven_zip_program(), "l", "-slt", "-ba", "-sccUTF-8", str(path)],
                             check=True, capture_output=True).stdout.decode("utf-8")
    entries = []
    for block in listing.replace("\r\n", "\n").split("\n\n"):
        fields = dict(line.split(" = ", 1) for line in block.splitlines() if " = " in line)
        if "Path" not in fields or fields.get("Folder") == "+" or "D" in fields.get("Attributes", ""):
            continue
        entries.append((fields["Path"].replace("\\", "/"), int(fields["Size"])))
    return entries


def seven_zip_digest(path, name, kind):
    """The content ID of one .7z member, streamed out of 7-Zip without writing it to disk."""
    process = subprocess.Popen([seven_zip_program(), "e", "-so", "-spd", "-sccUTF-8", str(path), "--", name],
                               stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    content_id = digest(process.stdout, kind)
    error = process.stderr.read()
    if process.wait():
        raise RuntimeError(f"7-Zip could not read {name}: {error.decode('utf-8', 'replace').strip()}")
    return content_id


def scan(root, media, previous=None):
    """Scan direct media files and ordinary ZIPs; nested ZIPs are queued for later. A previous
    scan's IDs are reused for files whose size and modification time are unchanged."""
    root = Path(root).resolve()
    old = {item["path"]: item for item in (previous or {}).get("archives", [])}
    formats_current = (previous or {}).get("mediaFormatsVersion", 1) >= media.version
    results = []
    archives = (".zip", ".7z") if media.seven_zip else (".zip",)
    for path in sorted(root.rglob("*")):
        if not path.is_file() or (path.suffix.lower() not in archives and not media.kind(path.name)):
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
            if path.suffix.lower() == ".7z" and media.seven_zip:
                entries = seven_zip_entries(path)
                sizes = {name.lower(): (name, size) for name, size in entries}
                for name, _ in entries:
                    parts = name.split("/")
                    if name.startswith("/") or any(part in ("", ".", "..") for part in parts) or re.match(r"^[a-zA-Z]:", name):
                        raise ValueError("unsafe 7z entry")
                    if name.lower().endswith((".zip", ".7z")):
                        item["nestedZip"] = True
                    kind = media.kind(name)
                    if not kind or media.superseded(name, {key: value for key, (value, _) in sizes.items()}):
                        continue
                    data_name = media.data_for(name, kind, {key: value for key, (value, _) in sizes.items()})
                    data = (name, dict(entries)[name]) if data_name == name else sizes.get(data_name.lower())
                    if data is None:
                        raise ValueError(f"{data_name} is missing")
                    if data[1] <= 0 or data[1] > media.limit(kind):
                        raise ValueError("disk image exceeds size limit")
                    content_id = cached_disks.get(name) or seven_zip_digest(path, data[0], kind)
                    item["disks"].append({"entry": name, "contentId": content_id})
            elif path.suffix.lower() != ".zip":
                kind = media.kind(path.name)
                present = {sibling.name.lower(): sibling.name for sibling in path.parent.iterdir()}
                if media.superseded(path.name, present):
                    continue
                data = path.with_name(media.data_for(path.name, kind, present))
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
                    present = {info.filename.lower(): info.filename for info in entries}
                    for info in entries:
                        if not valid_entry(info):
                            raise ValueError("unsafe ZIP entry")
                        if info.filename.lower().endswith(".zip"):
                            item["nestedZip"] = True
                        kind = media.kind(info.filename)
                        if not kind or media.superseded(info.filename, present):
                            continue
                        data_name = media.data_for(info.filename, kind, present)
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
