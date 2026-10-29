import gzip
import tempfile
import unittest
from pathlib import Path

import catalog_files


class CatalogFilesTest(unittest.TestCase):
    def test_a_gz_file_is_written_gzipped_and_read_back(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "descriptions-v1.json.gz"
            catalog_files.write(path, {"schemaVersion": 1, "descriptions": {"a:1": "A space game."}})
            self.assertEqual(path.read_bytes()[:2], b"\x1f\x8b")
            self.assertEqual(catalog_files.read(path), {"schemaVersion": 1, "descriptions": {"a:1": "A space game."}})

    def test_the_same_content_gives_the_same_bytes(self):
        with tempfile.TemporaryDirectory() as folder:
            one, other = Path(folder) / "one.json.gz", Path(folder) / "other.json.gz"
            catalog_files.write_bytes(one, b'{"a":1}')
            catalog_files.write_bytes(other, b'{"a":1}')
            self.assertEqual(one.read_bytes(), other.read_bytes())

    def test_a_file_holding_the_content_is_left_alone(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "data.json.gz"
            # Bytes another zlib might have written: still the same content, so not rewritten.
            path.write_bytes(gzip.compress(b'{"a":1}', compresslevel=1, mtime=0))
            before = path.read_bytes()
            catalog_files.write_bytes(path, b'{"a":1}')
            self.assertEqual(path.read_bytes(), before)
            self.assertTrue(catalog_files.same(path, b'{"a":1}'))

    def test_only_the_parts_that_hold_descriptions_are_gzipped(self):
        root = Path("app")
        self.assertEqual(catalog_files.part(root, "data.nsfw").name, "data.nsfw.json.gz")
        self.assertEqual(catalog_files.part(root, "data").name, "data.json.gz")
        self.assertEqual(catalog_files.part(root, "controls").name, "controls.json")

    def test_a_plain_json_file_reads_as_it_is(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "controls.json"
            catalog_files.write_bytes(path, b'{"a":1}')
            self.assertEqual(path.read_bytes(), b'{"a":1}')
            self.assertEqual(catalog_files.read(path), {"a": 1})


if __name__ == "__main__":
    unittest.main()
