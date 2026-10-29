"""A catalog record's startup automation (Kairo#83), validated as the apps validate it
(shared frontend StartupAutomation.kt): "startupChoices", the game's startup menus, and "diskSwaps",
the disks the frontend changes for the player when the game asks for them.

An app's tools/catalog_config.py names its swaps' drives in DISK_SWAPS, {media slot: {content ID
kinds}}, as its catalog schema's CatalogSetting.DiskSwaps does; an app without one carries no swaps.
"""

import re

from catalog_documents import require

SHORT_ID = re.compile(r"[a-z0-9-]{1,40}\Z")
KEY = re.compile(r"[A-Za-z0-9]\Z")
SWAP_KEY = re.compile(r"[A-Za-z0-9]?\Z")
HASH = re.compile(r"[0-9a-f]{16}\Z")


def _label(value):
    return isinstance(value, str) and value.strip() != "" and len(value) <= 100


def _hashes(value):
    return isinstance(value, list) and 1 <= len(value) <= 16 and all(isinstance(h, str) and HASH.match(h) for h in value)


def validate_choices(choices):
    require(isinstance(choices, list) and len(choices) <= 4, "startupChoices must list at most 4 choices")
    for choice in choices:
        require(isinstance(choice, dict) and SHORT_ID.match(str(choice.get("id"))) and _label(choice.get("title")) and
                _hashes(choice.get("screenHashes")), "invalid startup choice")
        options = choice.get("options")
        require(isinstance(options, list) and 1 <= len(options) <= 12, "a startup choice has 1-12 options")
        for option in options:
            require(isinstance(option, dict) and SHORT_ID.match(str(option.get("id"))) and _label(option.get("label")),
                    "invalid startup option")
            if "steps" in option:
                steps = option["steps"]
                require("key" not in option and "enter" not in option and isinstance(steps, list) and 2 <= len(steps) <= 4 and
                        all(isinstance(step, dict) and set(step) == {"key", "enter", "screenHashes"} and
                            KEY.match(str(step["key"])) and type(step["enter"]) is bool and _hashes(step["screenHashes"])
                            for step in steps), "invalid startup option steps")
            else:
                require(KEY.match(str(option.get("key"))) and type(option.get("enter")) is bool, "invalid startup option key")
        require(len({option["id"] for option in options}) == len(options), "startup option IDs repeat")
    require(len({choice["id"] for choice in choices}) == len(choices), "startup choice IDs repeat")


def validate_swaps(swaps, drives, content_id):
    """[drives]: {media slot: {content ID kinds}} (catalog_config.DISK_SWAPS)."""
    require(isinstance(swaps, list) and len(swaps) <= 16, "diskSwaps must list at most 16 swaps")
    for swap in swaps:
        require(isinstance(swap, dict) and set(swap) == {"id", "drive", "contentId", "screenHashes", "key", "enter"} and
                SHORT_ID.match(str(swap["id"])) and type(swap["drive"]) is int and swap["drive"] in drives and
                isinstance(swap["contentId"], str) and content_id.fullmatch(swap["contentId"]) and
                swap["contentId"].split("-")[1] in drives[swap["drive"]] and _hashes(swap["screenHashes"]) and
                isinstance(swap["key"], str) and SWAP_KEY.match(swap["key"]) and type(swap["enter"]) is bool,
                "invalid disk swap")
    require(len({swap["id"] for swap in swaps}) == len(swaps), "disk swap IDs repeat")
    screens = [h for swap in swaps for h in swap["screenHashes"]]
    require(len(set(screens)) == len(screens), "a screen asks for two disk swaps")
