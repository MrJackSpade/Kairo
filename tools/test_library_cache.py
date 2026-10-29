import struct
import tempfile
import unittest
from pathlib import Path

import library_cache


def text(value):
    data = value.encode("utf-8")
    return struct.pack(">i", len(data)) + data


def optional(value):
    return struct.pack(">?", value is not None) + (text(value) if value is not None else b"")


def record(layout, zip_entry=None, folder=()):
    data = text("Games/A.zip/a.hdm") + text("content://a") + text("Games/A.zip") + optional(zip_entry)
    data += struct.pack(">qqqq", 10, 20, 30, 40) + optional("sha256-fd-v1:" + "a" * 64) + optional(None)
    data += struct.pack(">i", 1) + text("content://a.img") + optional(None)
    if layout >= 5:
        data += struct.pack(">?", False)
    if layout >= 6:
        data += struct.pack(">i", len(folder)) + b"".join(text(name) + text(source) for name, source in folder)
    return data


def cache(layout, records, magic=library_cache.MAGIC):
    return (struct.pack(">Ii", magic, layout) + text("content://tree") + text("{}") +
            struct.pack(">i", len(records)) + b"".join(records))


class LibraryCacheTest(unittest.TestCase):
    def read(self, data):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "library-v2.bin"
            path.write_bytes(data)
            return library_cache.read(path)

    def test_reads_the_layout_the_app_writes(self):
        result = self.read(cache(6, [record(6, "a.hdm", [("RUN.EXE", "content://run")])]))
        self.assertEqual(result["treeUri"], "content://tree")
        entry = result["entries"][0]
        self.assertEqual(entry["zipEntry"], "a.hdm")
        self.assertEqual(entry["contentId"], "sha256-fd-v1:" + "a" * 64)
        self.assertIsNone(entry["error"])
        self.assertEqual(entry["companions"], ["content://a.img"])
        self.assertFalse(entry["holdsGame"])
        self.assertEqual(entry["folder"], [("RUN.EXE", "content://run")])

    def test_reads_earlier_layouts(self):
        self.assertTrue(self.read(cache(4, [record(4)]))["entries"][0]["holdsGame"])
        self.assertFalse(self.read(cache(5, [record(5)]))["entries"][0]["holdsGame"])

    def test_refuses_what_the_app_would_not_read(self):
        with self.assertRaises(ValueError):
            self.read(cache(2, []))
        with self.assertRaises(ValueError):
            self.read(cache(6, [], magic=0x12345678))
        with self.assertRaises(ValueError):
            self.read(cache(6, [record(6)]) + b"\0")


if __name__ == "__main__":
    unittest.main()
