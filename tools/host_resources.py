"""The shared PC boot test's resources (Kairo#115): an app's UI test bundle, unpacked, with every
ZIP and 7z archive in it also extracted beside itself (games/X.zip -> games/X/...), since the app
hands its core the disk images inside them, not the archives.

usage: host_resources.py <bundle> <folder>
"""
import sys
import zipfile
from pathlib import Path

import py7zr

import test_resources


def extract(archive: Path) -> None:
    target = archive.with_suffix("")
    if target.exists():
        return
    partial = target.with_name(target.name + ".part")
    if archive.suffix.lower() == ".zip":
        with zipfile.ZipFile(archive) as z:
            for entry in z.infolist():
                if not (partial / entry.filename).resolve().is_relative_to(partial.resolve()):
                    raise SystemExit(f"{entry.filename} escapes {archive}")
            z.extractall(partial)
    else:
        with py7zr.SevenZipFile(archive) as z:
            for name in z.getnames():
                if not (partial / name).resolve().is_relative_to(partial.resolve()):
                    raise SystemExit(f"{name} escapes {archive}")
            z.extractall(partial)
    partial.rename(target)
    print(f"{archive.name}: extracted")


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    folder = Path(sys.argv[2])
    test_resources.unpack(Path(sys.argv[1]), folder, host=True)
    for archive in sorted(folder.rglob("*")):
        if archive.is_file() and archive.suffix.lower() in (".zip", ".7z"):
            extract(archive)


if __name__ == "__main__":
    main()
