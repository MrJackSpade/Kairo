#!/usr/bin/env python3
"""Content IDs of game media in a folder of images and ZIPs, without extracting anything.

A content ID is "sha256-<kind>-v1:" followed by the SHA-256 of the medium's bytes as stored
(inside a ZIP: the entry's uncompressed bytes); a CD kept as a CHD hashes the disc image it was
made from (chd_image), and a CUE that names several track files (Redump's one BIN per track)
hashes them joined in the sheet's order, which is what its CHD reads back as. Each app names its kinds and which files carry
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

import chd_image

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
    match the shared library's DiskImageLibrary.Format data and supersededBy.

    probe, given the first PROBE_BYTES bytes and the size of track data no descriptor reads (a .bin
    no sheet names), returns its kind or None: a floppy a dump kept as .bin beside its CD's sheet
    (KairoTowns#103). It matches the shared library's Format.probe."""
    kind: Callable[[str], Optional[str]]
    data: Callable[[str, str], str]
    limit: Callable[[str], int]
    version: int
    seven_zip: bool = False
    candidates: dict = field(default_factory=dict)
    superseded_by: dict = field(default_factory=dict)
    probe: Optional[Callable[[bytes, int], Optional[str]]] = None

    def is_data(self, name):
        """Whether name is track data: no kind of its own, and a descriptor's data suffix."""
        suffix = Path(name).suffix.lower()
        return not self.kind(name) and any(suffix in suffixes for suffixes in self.candidates.values())

    def probe_kind(self, name, size, read_head):
        """The kind probe gives track data name, or None; read_head() returns its first bytes."""
        if self.probe is None or not self.is_data(name) or size <= 0:
            return None
        return self.probe(read_head(), size)

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

    def track_files(self, name, kind, present, read_sheet):
        """The members whose bytes name's ID hashes, in order, from present (lower-case name to
        name): the track data beside a descriptor under its own name (data_for), as before; a CUE
        without that is read, and the files its FILE lines name (Redump's one BIN per track) are its
        data, resolved beside it, in the sheet's order. read_sheet() returns the sheet's text. The
        apps resolve sheets the same way (shared DiscDescriptors.trackFiles)."""
        suffix = Path(name).suffix.lower()
        if suffix != ".cue" or suffix not in self.candidates or any(
                (name[:-len(suffix)] + data).lower() in present for data in self.candidates[suffix]):
            return [self.data_for(name, kind, present)]
        files = cue_files(read_sheet())
        if not files:
            raise ValueError(f"{Path(name).name} names no track data")
        folder = name.rsplit("/", 1)[0] + "/" if "/" in name else ""
        resolved = [present.get((folder + file.replace("\\", "/")).lower()) for file in files]
        missing = [file for file, found in zip(files, resolved) if not found]
        if missing:
            raise ValueError(f"the track data of {Path(name).name} is missing: {missing[0]}")
        return resolved

    def superseded(self, name, present):
        suffix = Path(name).suffix.lower()
        base = name[:-len(suffix)] if suffix else name
        return any((base + other).lower() in present for other in self.superseded_by.get(suffix, ()))


CUE_FILE = re.compile(r'^\s*FILE\s+(?:"([^"]+)"|(\S+))', re.IGNORECASE | re.MULTILINE)


def cue_files(text):
    """The track files a cue sheet names, in its order."""
    return [quoted or bare for quoted, bare in CUE_FILE.findall(text)]


PROBE_BYTES = 512


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
    return digest_all([lambda: stream], kind, close=False)


def digest_all(opens, kind, close=True):
    """The content ID of the streams opens give, read one after another as one medium."""
    sha = hashlib.sha256()
    for open_stream in opens:
        stream = open_stream()
        try:
            while block := stream.read(CHUNK):
                sha.update(block)
        finally:
            if close:
                stream.close()
    return f"sha256-{kind}-v1:" + sha.hexdigest()


def member_digest(stream, name, kind):
    """The content ID of an archive member read from stream."""
    return chd_image.digest_stream(stream, kind) if chd_image.is_chd(name) else digest(stream, kind)


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
    if chd_image.is_chd(name):
        process = seven_zip_member(path, name)
        content_id = member_digest(process.stdout, name, kind)
        seven_zip_finish(process, name)
        return content_id
    return seven_zip_digest_all(path, [name], kind)


def seven_zip_member(path, name):
    return subprocess.Popen([seven_zip_program(), "e", "-so", "-spd", "-sccUTF-8", str(path), "--", name],
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE)


def seven_zip_finish(process, name):
    error = process.stderr.read()
    if process.wait():
        raise RuntimeError(f"7-Zip could not read {name}: {error.decode('utf-8', 'replace').strip()}")


def seven_zip_digest_all(path, names, kind):
    """The content ID of .7z members read one after another as one medium."""
    sha = hashlib.sha256()
    for name in names:
        process = seven_zip_member(path, name)
        while block := process.stdout.read(CHUNK):
            sha.update(block)
        seven_zip_finish(process, name)
    return f"sha256-{kind}-v1:" + sha.hexdigest()


def seven_zip_head(path, name):
    """A .7z member's first PROBE_BYTES bytes; 7-Zip is stopped once they are read."""
    process = seven_zip_member(path, name)
    head = process.stdout.read(PROBE_BYTES)
    process.kill()
    process.wait()
    return head


def loose_track_data(folder, media, present):
    """The files, by lower-case name, that the descriptors in folder read as track data."""
    read = set()
    for name in present.values():
        kind = media.kind(name)
        if kind and Path(name).suffix.lower() in media.candidates:
            sheet = folder / name
            read.update(data.lower() for data in media.track_files(
                name, kind, present, lambda sheet=sheet: sheet.read_text(encoding="utf-8", errors="replace")))
    return read


def seven_zip_text(path, name):
    process = seven_zip_member(path, name)
    text = process.stdout.read().decode("utf-8", "replace")
    seven_zip_finish(process, name)
    return text


def scan(root, media, previous=None, top_level=False):
    """Scan direct media files and ordinary ZIPs; nested ZIPs are queued for later. A previous
    scan's IDs are reused for files whose size and modification time are unchanged. top_level
    scans only the folder's own files, not its subfolders."""
    root = Path(root).resolve()
    old = {item["path"]: item for item in (previous or {}).get("archives", [])}
    formats_current = (previous or {}).get("mediaFormatsVersion", 1) >= media.version
    results = []
    archives = (".zip", ".7z") if media.seven_zip else (".zip",)
    for path in sorted(root.glob("*") if top_level else root.rglob("*")):
        if not path.is_file() or (path.suffix.lower() not in archives and not media.kind(path.name)
                                  and not (media.probe and media.is_data(path.name))):
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
                read = set()
                for name, _ in entries:
                    parts = name.split("/")
                    if name.startswith("/") or any(part in ("", ".", "..") for part in parts) or re.match(r"^[a-zA-Z]:", name):
                        raise ValueError("unsafe 7z entry")
                    if name.lower().endswith((".zip", ".7z")):
                        item["nestedZip"] = True
                    kind = media.kind(name)
                    if not kind or media.superseded(name, {key: value for key, (value, _) in sizes.items()}):
                        continue
                    names = media.track_files(name, kind, {key: value for key, (value, _) in sizes.items()},
                                              lambda: seven_zip_text(path, name))
                    datas = []
                    for data_name in names:
                        data = (name, dict(entries)[name]) if data_name == name else sizes.get(data_name.lower())
                        if data is None:
                            raise ValueError(f"{data_name} is missing")
                        datas.append(data)
                    total = sum(size for _, size in datas)
                    if any(size <= 0 for _, size in datas) or total > media.limit(kind):
                        raise ValueError("disk image exceeds size limit")
                    read.update(data_name.lower() for data_name, _ in datas)
                    content_id = cached_disks.get(name) or (
                        seven_zip_digest(path, datas[0][0], kind) if len(datas) == 1
                        else seven_zip_digest_all(path, [data_name for data_name, _ in datas], kind))
                    item["disks"].append({"entry": name, "contentId": content_id})
                for name, size in entries:
                    if name.lower() in read:
                        continue
                    kind = media.probe_kind(name, size, lambda: seven_zip_head(path, name))
                    if kind is None:
                        continue
                    if size > media.limit(kind):
                        raise ValueError("disk image exceeds size limit")
                    content_id = cached_disks.get(name) or seven_zip_digest(path, name, kind)
                    item["disks"].append({"entry": name, "contentId": content_id})
            elif path.suffix.lower() != ".zip":
                kind = media.kind(path.name)
                present = {sibling.name.lower(): sibling.name for sibling in path.parent.iterdir()}
                if kind is None:
                    if path.name.lower() in loose_track_data(path.parent, media, present):
                        continue
                    kind = media.probe_kind(path.name, stat.st_size, lambda: path.open("rb").read(PROBE_BYTES))
                    if kind is None:
                        continue
                if media.superseded(path.name, present):
                    continue
                datas = [path.with_name(data_name) for data_name in
                         media.track_files(path.name, kind, present, lambda: path.read_text(encoding="utf-8", errors="replace"))]
                for data in datas:
                    if not data.is_file():
                        raise ValueError(f"{data.name} is missing")
                sizes = [data.stat().st_size for data in datas]
                if any(size <= 0 for size in sizes) or sum(sizes) > media.limit(kind):
                    raise ValueError("disk image exceeds size limit")
                content_id = cached_disks.get(None)
                if not content_id and chd_image.is_chd(datas[0].name):
                    content_id = chd_image.digest(datas[0], kind)
                elif not content_id:
                    content_id = digest_all([lambda data=data: data.open("rb") for data in datas], kind)
                item["disks"].append({"entry": None, "contentId": content_id})
            else:
                with zipfile.ZipFile(path) as archive:
                    entries = archive.infolist()
                    members = {info.filename.lower(): info for info in entries}
                    present = {info.filename.lower(): info.filename for info in entries}
                    read = set()
                    for info in entries:
                        if not valid_entry(info):
                            raise ValueError("unsafe ZIP entry")
                        if info.filename.lower().endswith(".zip"):
                            item["nestedZip"] = True
                        kind = media.kind(info.filename)
                        if not kind or media.superseded(info.filename, present):
                            continue
                        names = media.track_files(info.filename, kind, present,
                                                  lambda: archive.read(info).decode("utf-8", "replace"))
                        datas = []
                        for data_name in names:
                            data = info if data_name == info.filename else members.get(data_name.lower())
                            if data is None:
                                raise ValueError(f"{data_name} is missing")
                            # No expansion-ratio cap (Kairo#139): a silent track deflates at about 1024:1,
                            # and a member is read only up to its stated size, which the limit below bounds.
                            if data.file_size <= 0:
                                raise ValueError("disk image is empty")
                            datas.append(data)
                        if sum(data.file_size for data in datas) > media.limit(kind):
                            raise ValueError("disk image exceeds size limit")
                        read.update(data.filename.lower() for data in datas)
                        content_id = cached_disks.get(info.filename)
                        if not content_id and len(datas) == 1:
                            with archive.open(datas[0]) as source:
                                content_id = member_digest(source, datas[0].filename, kind)
                        elif not content_id:
                            content_id = digest_all([lambda data=data: archive.open(data) for data in datas], kind)
                        item["disks"].append({"entry": info.filename, "contentId": content_id})
                    for info in entries:
                        if info.is_dir() or info.filename.lower() in read:
                            continue
                        kind = media.probe_kind(info.filename, info.file_size,
                                                lambda: archive.open(info).read(PROBE_BYTES))
                        if kind is None:
                            continue
                        if info.file_size > media.limit(kind):
                            raise ValueError("disk image exceeds size limit")
                        content_id = cached_disks.get(info.filename)
                        if not content_id:
                            with archive.open(info) as source:
                                content_id = member_digest(source, info.filename, kind)
                        item["disks"].append({"entry": info.filename, "contentId": content_id})
            item["status"] = "ready" if item["disks"] else ("nested-zip" if item.get("nestedZip") else "no-supported-image")
        except (OSError, ValueError, zipfile.BadZipFile, RuntimeError) as error:
            item["status"] = "error"
            item["error"] = str(error)
        results.append(item)
    return {"schemaVersion": 1, "mediaFormatsVersion": media.version,
            "root": str(root), "archives": results}
