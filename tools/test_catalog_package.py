import json
import hashlib
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

from catalog_package import build_package, write_zip


class CatalogPackageTests(unittest.TestCase):
    def test_runtime_header_and_index_match_publishing_inventory(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            image = 'art/catalog/example.webp'
            (root / image).parent.mkdir(parents=True)
            (root / image).write_bytes(b'image')
            output = root / 'catalog.zip'
            build_package({'schemaVersion': 1, 'games': {}}, {image}, root, output,
                          product='dos', identity='fixture', name='Fixture', revision=3,
                          source='https://example.test/manifest.json',
                          archive_url='https://example.test/catalog.zip', stored=True)
            with zipfile.ZipFile(output) as archive:
                header = json.loads(archive.read('catalog.json'))
                self.assertEqual(json.loads(archive.read('runtime.json')),
                                 {k:v for k,v in header.items() if k != 'files'})
                self.assertEqual(archive.read('artwork.idx').decode().splitlines(), [image])
                self.assertEqual(set(archive.namelist()), {'catalog.json', *header['files']})
                for name, spec in header['files'].items():
                    payload = archive.read(name)
                    self.assertEqual(spec, {'size': len(payload), 'sha256': hashlib.sha256(payload).hexdigest()})

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
