"""Reads a disk-image app's library cache as a device writes it (files/library-v2.bin), so tools can use
a player's library: the shared frontend's VersionedLibraryCache holding DiskImageLibrary's records.

The file is big-endian: magic 0x4B4C4942, the record layout, the library's tree URI, a JSON text of
extras, the entry count, then each entry's record. Texts are a 32-bit length and UTF-8; an optional
text is a boolean, then the text when it is true. A record (DiskImageLibrary.writeCacheRecord) is:
id, uri, path, optional zip entry, source size, source modified, image size and image CRC (64-bit
each), optional content ID, optional error, the companions (a count and texts), optional catalog
ID; from layout 5 whether it holds a game; from layout 6 the program's folder (a count of name and
source texts). Any other layout fails, as the app would not read it either.

Pull a cache with: adb exec-out run-as <package> cat files/library-v2.bin > library-v2.bin
"""
import struct
from pathlib import Path

MAGIC = 0x4B4C4942
LAYOUTS = (4, 5, 6)


class _Reader:
    def __init__(self, data):
        self.data = data
        self.position = 0

    def take(self, fmt):
        value = struct.unpack_from(fmt, self.data, self.position)[0]
        self.position += struct.calcsize(fmt)
        return value

    def text(self):
        size = self.take(">i")
        value = self.data[self.position:self.position + size].decode("utf-8")
        self.position += size
        return value

    def optional(self):
        return self.text() if self.take(">?") else None


def read(path):
    """{"treeUri", "entries": [{"id", "uri", "path", "zipEntry", "contentId", "error", "companions",
    "catalogId", "holdsGame", "folder"}]} of a library cache file."""
    reader = _Reader(Path(path).read_bytes())
    if reader.take(">I") != MAGIC:
        raise ValueError(f"{path} is not a Kairo library cache")
    layout = reader.take(">i")
    if layout not in LAYOUTS:
        raise ValueError(f"{path} uses library cache layout {layout}; this reader knows {LAYOUTS}")
    tree = reader.text()
    reader.text()  # extras
    entries = []
    for _ in range(reader.take(">i")):
        entry = {"id": reader.text(), "uri": reader.text(), "path": reader.text(), "zipEntry": reader.optional()}
        for _ in range(4):
            reader.take(">q")
        entry["contentId"] = reader.optional()
        entry["error"] = reader.optional()
        entry["companions"] = [reader.text() for _ in range(reader.take(">i"))]
        entry["catalogId"] = reader.optional()
        entry["holdsGame"] = reader.take(">?") if layout >= 5 else True
        entry["folder"] = [(reader.text(), reader.text()) for _ in range(reader.take(">i"))] if layout >= 6 else []
        entries.append(entry)
    if reader.position != len(reader.data):
        raise ValueError(f"{path} has {len(reader.data) - reader.position} bytes after its last entry")
    return {"treeUri": tree, "entries": entries}
