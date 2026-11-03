import tempfile
import unittest
from pathlib import Path

import check_name_matching

# The two 1.4.2 bugs as they were written (Kairo98#110, KairoDos#126), and what the check leaves alone.
BOOT_DISK_PAIRING = '''
private val BOOT_DISK_NAME = Regex("(?i)\\\\bboot[ _-]*disk\\\\b")
val bootFloppies = siblings.filter { it.isFloppy && BOOT_DISK_NAME.containsMatchIn(it.displayName) }
    ?: playable.filter { it.displayName.contains("boot", true) }.singleOrNull()
'''
VARIANT_NAME = '''
override fun variantName(fileName: String) = super.variantName(fileName.removeSuffix(INSTALLER))
val record = variants.optJSONObject(variantName(entry.fileName))
'''
CLEAN = '''
// A comment may say Disk 1, Side A or boot disk.
val drive = CatalogSetting.Drive("bootDisk", UiText.res(R.string.boot_disk))
val image = files.filter { it.name.endsWith(".d88", true) }
/* the boot disk is named by the catalog */ val id = game.mediaId("bootDisk")
'''


class CheckNameMatchingTest(unittest.TestCase):
    def findings(self, code):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            source = root / "app" / "src" / "main" / "java" / "A.kt"
            source.parent.mkdir(parents=True)
            source.write_text(code, encoding="utf-8")
            # tools/ is not run time.
            tool = root / "tools" / "src" / "main" / "B.kt"
            tool.parent.mkdir(parents=True)
            tool.write_text(BOOT_DISK_PAIRING, encoding="utf-8")
            return check_name_matching.check(root)

    def test_the_boot_disk_pairing_fails(self):
        findings, _ = self.findings(BOOT_DISK_PAIRING)
        self.assertEqual([finding.split(": ")[1] for finding in findings],
                         ["a disk-number, side or boot-disk pattern", "a name given to a regex", "a name compared"])

    def test_the_variant_name_fails(self):
        findings, _ = self.findings(VARIANT_NAME)
        self.assertEqual(len(findings), 2)

    def test_ids_extensions_comments_and_tools_pass(self):
        self.assertEqual(self.findings(CLEAN), ([], []))


if __name__ == "__main__":
    unittest.main()
