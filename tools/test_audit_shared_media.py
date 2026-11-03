import unittest

import app_catalog
from audit_shared_media import problems

BLANK = "sha256-fd-v1:ff942b97"
TWIN_COBRA, MONKEY_ISLAND, TENSHOUKI = "fmtowns:188275", "fmtowns:144466", "fmtowns:157260"


def catalog(blank_record=None):
    """KairoTowns#83: three games' sets ship the same blank floppy."""
    records = {f"sha256-cd-v1:{index}": {"game": game, "setDisks": [BLANK, f"sha256-fd-v1:{index}"]}
               for index, game in enumerate((TWIN_COBRA, MONKEY_ISLAND, TENSHOUKI))}
    records.update({f"sha256-fd-v1:{index}": {"game": game} for index, game in enumerate((TWIN_COBRA, MONKEY_ISLAND, TENSHOUKI))})
    if blank_record is not None:
        records[BLANK] = blank_record
    return records


class AuditSharedMediaTest(unittest.TestCase):
    def test_shared_media_without_a_game_passes(self):
        self.assertEqual(problems(catalog()), [])

    def test_the_blank_floppy_given_back_to_twin_cobra_fails(self):
        found = problems(catalog({"game": TWIN_COBRA}))
        self.assertEqual(len(found), 1)
        self.assertIn("listed as fmtowns:188275", found[0])

    def test_a_disk_in_one_set_listed_as_another_game_fails(self):
        records = catalog()
        records["sha256-fd-v1:1"] = {"game": TENSHOUKI}
        found = problems(records)
        self.assertEqual(found, ["sha256-fd-v1:1 is in the set of fmtowns:144466 but is listed as the separate game fmtowns:157260"])


class SharedSetDisksTest(unittest.TestCase):
    """The generator leaves a disk in several games' sets unnamed (Kairo98's data editions)."""

    def test_a_disk_in_two_games_sets_is_shared(self):
        research = {"pc98:base": {"contentIds": ["a", "b", "c"], "diskSets": {"a": {"setDisks": ["b", "c"]}}},
                    "pc98:data": {"contentIds": ["d", "e"], "diskSets": {"d": {"setDisks": ["b", "e"]}}}}
        self.assertEqual(app_catalog.shared_set_disks(research), {"b": ["pc98:base", "pc98:data"]})


if __name__ == "__main__":
    unittest.main()
