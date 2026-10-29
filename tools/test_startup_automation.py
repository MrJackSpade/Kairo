"""Tests for startup_automation.py: the catalog's startup choices and disk swaps."""

import re
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import startup_automation

CONTENT_ID = re.compile(r"sha256-(?:fd|hd|tape)-v1:[0-9a-f]{64}\Z")
FLOPPY = "sha256-fd-v1:" + "a" * 64
DRIVES = {0: {"fd"}, 1: {"fd"}, 2: {"tape"}}


def swap(**fields):
    return {"id": "disk-2", "drive": 1, "contentId": FLOPPY, "screenHashes": ["0123456789abcdef"], "key": "",
            "enter": True, **fields}


def choice(**fields):
    return {"id": "start", "title": "Start", "screenHashes": ["0123456789abcdef"],
            "options": [{"id": "game", "label": "Start game", "key": "1", "enter": False}], **fields}


class StartupAutomationTest(unittest.TestCase):
    def test_a_swap_into_a_drive_that_takes_its_disk(self):
        startup_automation.validate_swaps([swap()], DRIVES, CONTENT_ID)

    def test_a_swap_into_a_drive_the_app_has_none_of_or_that_takes_other_disks(self):
        for bad in (swap(drive=5), swap(drive=2), swap(contentId="sha256-hd-v1:" + "a" * 64)):
            with self.assertRaises(ValueError):
                startup_automation.validate_swaps([bad], DRIVES, CONTENT_ID)

    def test_one_screen_asks_for_one_swap(self):
        with self.assertRaises(ValueError):
            startup_automation.validate_swaps([swap(), swap(id="disk-3", drive=0)], DRIVES, CONTENT_ID)

    def test_a_choice_with_a_key_or_with_steps(self):
        steps = [{"key": "1", "enter": False, "screenHashes": ["0123456789abcdef"]},
                 {"key": "2", "enter": True, "screenHashes": ["fedcba9876543210"]}]
        startup_automation.validate_choices([choice(), choice(id="mode", options=[{"id": "a", "label": "A", "steps": steps}])])

    def test_a_choice_needs_its_screens_and_distinct_ids(self):
        for bad in ([choice(screenHashes=["xyz"])], [choice(), choice()],
                    [choice(options=[{"id": "game", "label": "Start", "key": "!", "enter": False}])]):
            with self.assertRaises(ValueError):
                startup_automation.validate_choices(bad)

    def test_an_empty_list_turns_the_automation_off(self):
        startup_automation.validate_choices([])
        startup_automation.validate_swaps([], DRIVES, CONTENT_ID)


if __name__ == "__main__":
    unittest.main()
