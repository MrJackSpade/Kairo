import os
import tempfile
import unittest
import zipfile
from pathlib import Path

import check_scan_cache
import content_ids


def kind(name):
    return {".d88": "fd", ".cue": "cd", ".ccd": "cd"}.get(Path(name).suffix.lower())


MEDIA = content_ids.Media(kind, content_ids.same_file, lambda kind: 1 << 30, 1,
                          candidates={".cue": [".bin"], ".ccd": [".img"]}, superseded_by={".cue": {".ccd"}})


class CheckScanCacheTest(unittest.TestCase):
    """KairoTowns#83: an archive cached before its floppies were read (Kairo#108)."""

    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.root = Path(self.folder.name)
        with zipfile.ZipFile(self.root / "Game.zip", "w") as archive:
            archive.writestr("Game (Disk 1).d88", b"one")
            archive.writestr("Game (Boot).d88", b"boot")
            archive.writestr("Game.cue", b'FILE "Game.bin" BINARY')
            archive.writestr("Game.bin", b"track")
            archive.writestr("Other.cue", b"")
            archive.writestr("Other.ccd", b"")
            archive.writestr("Manual.txt", b"read me")
        stat = (self.root / "Game.zip").stat()
        self.archive = {"path": "Game.zip", "fingerprint": {"size": stat.st_size, "mtimeNs": stat.st_mtime_ns},
                        "status": "ready", "disks": [{"entry": name, "contentId": "x"} for name in
                                                     ("Game (Disk 1).d88", "Game (Boot).d88", "Game.cue", "Other.ccd")]}

    def tearDown(self):
        self.folder.cleanup()

    def check(self):
        return check_scan_cache.check_index({"root": str(self.root), "archives": [self.archive]}, MEDIA)

    def test_a_complete_cache_passes(self):
        self.assertEqual(self.check(), [])

    def test_a_floppy_missing_from_the_cache_fails(self):
        self.archive["disks"] = [disk for disk in self.archive["disks"] if disk["entry"] != "Game (Boot).d88"]
        self.assertEqual(self.check(), ["Game.zip: Game (Boot).d88 is not among its cached disks; scan it again"])

    def test_an_archive_cached_without_images_is_checked_too(self):
        self.archive["status"] = "no-supported-image"
        self.archive["disks"] = []
        self.assertEqual(len(self.check()), 4)

    def test_an_archive_changed_since_its_scan_fails(self):
        os.utime(self.root / "Game.zip", ns=(0, 0))
        self.assertEqual(self.check(), ["Game.zip: changed since it was scanned; scan it again"])


if __name__ == "__main__":
    unittest.main()
