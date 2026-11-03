#!/usr/bin/env python3
"""Checks an app's media scan caches against the archives they describe (Kairo#108).

KairoTowns#83: The Secret of Monkey Island's boot floppy showed as Twin Cobra. The scan cache held
four archives scanned before their floppies were read, and content_ids.scan reuses a cached archive
while its size and time are unchanged, so the floppies never came back.

    python3 shared/tools/check_scan_cache.py <app root>

For each scan the app's tools/catalog_config.py names (SCANS), every archive the cache read (ready, or
holding no supported image) is listed again, without hashing: every member the app's media kinds
accept must be among its cached disks, and the archive must be the size and time it was scanned at.
research.py runs it on each scan it reads, so a catalog is never built from a cache that misses disks.
"""
import sys
import zipfile
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import content_ids

# Archives are read over the network share in parallel; each listing waits on I/O, not the CPU.
WORKERS = 16
CHECKED = ("ready", "no-supported-image")


def members(path):
    """The member names of the archive at [path], or None for a loose image (it is its own medium)."""
    suffix = path.suffix.lower()
    if suffix == ".zip":
        with zipfile.ZipFile(path) as archive:
            return [info.filename for info in archive.infolist() if not info.is_dir()]
    if suffix == ".7z":
        return [name for name, _ in content_ids.seven_zip_entries(path)]
    return None


def problems(root, archive, media):
    """What the cache of [archive] (an entry of a scan index of [root]) misses, as messages."""
    path = Path(root) / archive["path"]
    stat = path.stat()
    if {"size": stat.st_size, "mtimeNs": stat.st_mtime_ns} != archive["fingerprint"]:
        return [f"{archive['path']}: changed since it was scanned; scan it again"]
    names = members(path)
    if names is None:
        return []
    present = {name.lower(): name for name in names}
    cached = {disk.get("entry") for disk in archive["disks"]}
    return [f"{archive['path']}: {name} is not among its cached disks; scan it again"
            for name in names if media.kind(name) and not media.superseded(name, present) and name not in cached]


def check_index(index, media):
    """Every problem of a scan [index] (a content_ids.scan result) with [media]'s rules."""
    archives = [archive for archive in index["archives"] if archive["status"] in CHECKED]
    with ThreadPoolExecutor(WORKERS) as pool:
        found = pool.map(lambda archive: problems(index["root"], archive, media), archives)
        return [problem for archive_problems in found for problem in archive_problems]


def main(argv):
    if len(argv) != 2:
        raise SystemExit("usage: check_scan_cache.py <app root>")
    import research
    root = Path(argv[1]).resolve()
    config = research.load_config(root)
    failed = False
    for set_name, (index_path, _) in config.SCANS.items():
        index = research.read(root / index_path, None)
        if index is None:
            raise SystemExit(f"missing media scan {index_path}")
        found = check_index(index, config.MEDIA)
        for problem in found:
            print(f"{set_name}: {problem}", file=sys.stderr)
        print(f"{set_name}: {len(index['archives'])} archives, {len(found)} problems")
        failed |= bool(found)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
