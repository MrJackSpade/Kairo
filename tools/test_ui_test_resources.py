import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import test_resources


class UiTestResourcesTest(unittest.TestCase):
    """Kairo98#126: host/ files are in the bundle for the shared PC boot test, not the device's APK."""

    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.root = Path(self.folder.name)
        (self.root / "uitest").mkdir()
        files = {}
        for name in ("games/Game.d88", "bios/bios.rom", "host/Prompt.hdi"):
            source = self.root / "sources" / name
            source.parent.mkdir(parents=True, exist_ok=True)
            source.write_bytes(name.encode())
            files[name] = str(source)
        (self.root / "uitest/resources.json").write_text(json.dumps({"schemaVersion": 1, "files": files}))
        self.token = mock.patch.dict(os.environ, {test_resources.TOKEN: "test token"})
        self.token.start()
        test_resources.pack(self.root)
        self.bundle = self.root / "uitest/TestResources.part1.dat"

    def tearDown(self):
        self.token.stop()
        self.folder.cleanup()

    def unpacked(self, folder):
        return sorted(p.relative_to(folder).as_posix() for p in folder.rglob("*") if p.is_file() and
                      p.name != test_resources.MARKER)

    def test_the_device_gets_no_host_files(self):
        folder = self.root / "device"
        test_resources.unpack(self.bundle, folder)
        self.assertEqual(self.unpacked(folder), ["bios/bios.rom", "games/Game.d88"])

    def test_the_pc_gets_every_file(self):
        folder = self.root / "pc"
        test_resources.unpack(self.bundle, folder, host=True)
        self.assertEqual(self.unpacked(folder), ["bios/bios.rom", "games/Game.d88", "host/Prompt.hdi"])
        self.assertEqual((folder / "host/Prompt.hdi").read_bytes(), b"host/Prompt.hdi")

    def test_other_folders_are_refused(self):
        manifest = json.loads((self.root / "uitest/resources.json").read_text())
        manifest["files"]["other/File.bin"] = manifest["files"]["games/Game.d88"]
        (self.root / "uitest/resources.json").write_text(json.dumps(manifest))
        with self.assertRaises(SystemExit):
            test_resources.pack(self.root)


if __name__ == "__main__":
    unittest.main()
