#!/usr/bin/env python3
"""Writes an app's THIRD_PARTY_NOTICES.txt from the license files and notices in its third_party/.

  notices.py <app root> [--check]   (--check fails when the file is out of date)

The app lists its sources in tools/notices_sources.py: OUT, the notices file's path from the app
root; HEADER, its opening lines; and SECTIONS, each (title, source, [text, ...]) in the order shown,
built with text() and excerpt() below, which read the app's third_party/. The shared frontend's own
notices (its libraries, font, NDK runtime and AndroidX) follow the app's in About -> Licenses.
shared/tools/check-app.sh runs it with --check for every app.
"""
import argparse
import importlib.util
import sys
from pathlib import Path

RULE = "=" * 72
# The app root whose third_party/ text() and excerpt() read; set before its sources load.
ROOT = None


def text(path, encoding="utf-8"):
    """A file under third_party/, as the notices show it: LF line endings, no trailing spaces."""
    content = (ROOT / "third_party" / path).read_bytes().decode(encoding, errors="strict")
    # Spaces and tabs only: a license's form feeds (the GPL's page breaks) stay.
    lines = [line.rstrip(" \t") for line in content.replace("\r\n", "\n").split("\n")]
    return "\n".join(lines).strip("\n")


def excerpt(path, first, last, encoding="utf-8"):
    """The lines of a file under third_party/ from the one containing `first` through the next containing `last`."""
    lines = text(path, encoding).split("\n")
    start = next(i for i, line in enumerate(lines) if first in line)
    end = next(i for i in range(start, len(lines)) if last in lines[i])
    return "\n".join(lines[start:end + 1])


def render(sources):
    sections = ["\n\n".join([f"{RULE}\n{title}\nSource: {source}\n{RULE}"] + texts)
                for title, source, texts in sources.SECTIONS]
    return sources.HEADER.strip("\n") + "\n\n" + "\n\n".join(sections) + "\n"


def main():
    global ROOT
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("root", type=Path)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    ROOT = args.root.resolve()
    # The app's sources import this module by name: they get this one, with ROOT set.
    sys.modules["notices"] = sys.modules[__name__]
    spec = importlib.util.spec_from_file_location("notices_sources", ROOT / "tools/notices_sources.py")
    sources = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(sources)
    out = ROOT / sources.OUT
    content = render(sources)
    if args.check:
        # Git may check the file out with CRLF line endings.
        if out.read_text(encoding="utf-8").replace("\r\n", "\n") != content:
            raise SystemExit(f"{sources.OUT} is out of date: run shared/tools/notices.py .")
        print(f"{sources.OUT} is current")
        return
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(content, encoding="utf-8", newline="\n")
    print(f"wrote {sources.OUT}")


if __name__ == "__main__":
    main()
