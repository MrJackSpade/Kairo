import tempfile
import unittest
from pathlib import Path

import catalog_descriptions
import catalog_files


def write(path, descriptions):
    catalog_files.write(path, {"schemaVersion": 1, "descriptions": descriptions})


class CatalogDescriptionsTest(unittest.TestCase):
    def test_translations_carry_their_english_and_ship_the_text(self):
        with tempfile.TemporaryDirectory() as folder:
            research = Path(folder)
            write(research / "descriptions-v1.json.gz", {"a:1": "A space game."})
            write(research / "descriptions-es-v1.json.gz", {"a:1": {"en": "A space game.", "text": "Un juego espacial."}})
            games = {"a:1": {}}
            catalog_descriptions.apply(research, games)
            self.assertEqual(games["a:1"], {"description": "A space game.", "descriptions": {"es": "Un juego espacial."}})

    def test_a_translation_of_changed_english_is_stale(self):
        entries = {"a:1": {"en": "A space game.", "text": "Un juego espacial."},
                   "a:2": {"en": "A car game.", "text": "Un juego de coches."}}
        self.assertEqual(catalog_descriptions.stale({"a:1": "A space shooter.", "a:2": "A car game."}, entries), ["a:1"])

    def test_a_translation_without_its_english_fails(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "descriptions-es-v1.json.gz"
            write(path, {"a:1": "Un juego espacial."})
            with self.assertRaises(SystemExit):
                catalog_descriptions.translations(path)


if __name__ == "__main__":
    unittest.main()
