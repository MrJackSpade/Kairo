"""The catalog files that hold game descriptions, kept gzipped.

A session that reads catalog data by accident (a file read, a search) must not see a game's
description, original or translated: some translated text has poisoned a working session. Every
file that holds them is stored gzipped, and every step unzips it, works on it and rezips it; none
writes a plain copy. Searches and file reads skip them.

  catalog/research/descriptions-v1.json.gz, descriptions-<language>-v1.json.gz,
  descriptions-adult-v1.json.gz, descriptions-adult-<language>-v1.json.gz
                                        the descriptions themselves
  catalog/research/games-v1.json.gz     the research, which carries them into each game
  catalog/parts/data.json.gz, data.nsfw.json.gz
                                        the generated parts that hold them (the Gradle build reads
                                        these too: gradle/catalog-binary.gradle)

The frozen catalogs earlier app versions download (core-v2.json, core-v3.json, online-v1.json) stay
as they were published; they hold only English descriptions.
"""

import gzip
import json

# The generated parts kept gzipped: those that hold descriptions.
GZIPPED_PARTS = ("data", "data.nsfw")


def research(root, name):
    """The gzipped research file <name> (such as "games-v1.json") of an app root."""
    return root / "catalog/research" / f"{name}.gz"


def part(root, name):
    """A generated part's JSON: <name>.json.gz for those that hold descriptions, else <name>.json."""
    return root / "catalog/parts" / (f"{name}.json.gz" if name in GZIPPED_PARTS else f"{name}.json")


def read_bytes(path):
    return gzip.decompress(path.read_bytes()) if path.name.endswith(".gz") else path.read_bytes()


def read(path, default=None):
    """The JSON document at [path], unzipped when it is .gz; [default] when there is no file."""
    if default is not None and not path.is_file():
        return default
    return json.loads(read_bytes(path).decode("utf-8"))


def same(path, content):
    """Whether the file at [path] holds [content] (bytes), unzipped when it is .gz."""
    return path.is_file() and read_bytes(path) == content


def write_bytes(path, content):
    """Writes [content] (bytes) to [path], gzipped when it is .gz. A file that already holds the
    content is left as it is, so its gzip bytes do not change from one zlib to another."""
    if same(path, content):
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    temporary.write_bytes(gzip.compress(content, compresslevel=9, mtime=0) if path.name.endswith(".gz") else content)
    temporary.replace(path)


def write_text(path, text):
    write_bytes(path, text.encode("utf-8"))


def write(path, document):
    """Writes a research document the way every research file is written: sorted, one-space indent."""
    write_text(path, json.dumps(document, ensure_ascii=False, indent=1, sort_keys=True) + "\n")
