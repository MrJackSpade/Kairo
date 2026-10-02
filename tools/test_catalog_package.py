import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

from catalog_package import write_zip


class CatalogPackageTests(unittest.TestCase):
    def test_archive_bytes_do_not_depend_on_host_platform(self):
        original = zipfile.ZipInfo
        with tempfile.TemporaryDirectory() as directory:
            for stored in (False, True):
                outputs = []
                for host in (0, 3):
                    class HostZipInfo(original):
                        def __init__(self, *args, **kwargs):
                            super().__init__(*args, **kwargs)
                            self.create_system = host
                    path = Path(directory) / f'{stored}-{host}.zip'
                    with patch('catalog_package.zipfile.ZipInfo', HostZipInfo):
                        write_zip(path, {'data.json': b'{"title":"Example"}',
                                         'art/catalog/example.webp': b'image bytes'}, stored=stored)
                    outputs.append(path.read_bytes())
                    with zipfile.ZipFile(path) as archive:
                        self.assertEqual(archive.read('data.json'), b'{"title":"Example"}')
                        for entry in archive.infolist():
                            self.assertEqual(entry.create_system, 3)
                            self.assertEqual(entry.external_attr, 0o600 << 16)
                self.assertEqual(*outputs)


if __name__ == '__main__':
    unittest.main()
