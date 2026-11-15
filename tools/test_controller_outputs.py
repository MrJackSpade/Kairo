"""Tests for the shipped controller mappings' rules (app_catalog.py): one press sends one thing, and
a system mapping that leaves a D-pad direction unset fails."""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import app_catalog

ARROWS = {"up": 1, "down": 2, "left": 3, "right": 4}
DOS_MODIFIERS = frozenset({304, 306, 308})


def system(*bindings):
    return {"schemaVersion": 2, "controller": {"bindings": list(bindings)}}


def dpad(**fields):
    return [{"input": f"virtual:{direction}", **fields} for direction in ARROWS]


class SingleOutputTest(unittest.TestCase):
    def test_one_key_one_joystick_one_mouse_one_action_one_cycle(self):
        for binding in ({"input": "virtual:a", "keys": [13]}, {"input": "virtual:up", "joystick": "up"},
                        {"input": "virtual:l1", "mouse": "leftButton"}, {"input": "virtual:menu", "action": "menu"},
                        {"input": "virtual:l1", "cycleKeys": [49, 51, 53]},
                        {"input": "virtual:lsup", "mouse": "moveUp", "mouseSpeed": 1}):
            self.assertTrue(app_catalog.single_output(binding), binding)

    def test_keys_and_a_joystick_on_one_press(self):
        self.assertFalse(app_catalog.single_output({"input": "virtual:up", "keys": [273], "joystick": "joy1up"}))

    def test_two_keys_on_one_press(self):
        self.assertFalse(app_catalog.single_output({"input": "virtual:up", "keys": [147, 136]}))
        self.assertFalse(app_catalog.single_output({"input": "virtual:up", "keys": [273, 264]}, DOS_MODIFIERS))

    def test_keys_and_a_mouse_or_an_action(self):
        self.assertFalse(app_catalog.single_output({"input": "virtual:a", "keys": [13], "mouse": "leftButton"}))
        self.assertFalse(app_catalog.single_output({"input": "virtual:a", "keys": [13], "action": "menu"}))

    def test_a_mouse_speed_only_with_a_mouse(self):
        self.assertFalse(app_catalog.single_output({"input": "virtual:a", "keys": [13], "mouseSpeed": 1}))

    def test_a_key_typed_with_the_apps_modifiers_held_is_one_keystroke(self):
        self.assertTrue(app_catalog.single_output({"input": "virtual:b", "keys": [306, 112]}, DOS_MODIFIERS))
        self.assertTrue(app_catalog.single_output({"input": "virtual:b", "keys": [306, 308, 112]}, DOS_MODIFIERS))

    def test_a_modifier_chord_in_an_app_without_modifiers_is_two_keys(self):
        self.assertFalse(app_catalog.single_output({"input": "virtual:up", "keys": [53, 8]}))

    def test_no_output_or_an_empty_key_list(self):
        self.assertFalse(app_catalog.single_output({"input": "virtual:a"}))
        self.assertFalse(app_catalog.single_output({"input": "virtual:a", "keys": []}))


class SystemDpadTest(unittest.TestCase):
    def test_a_system_leaving_a_dpad_direction_unset_fails(self):
        with self.assertRaises(ValueError):
            app_catalog.validate_system(system({"input": "virtual:a", "keys": [13]}))
        with self.assertRaises(ValueError):
            app_catalog.validate_system(system({"input": "virtual:up", "keys": [1]}))

    def test_a_system_dpad_need_not_send_the_arrows(self):
        app_catalog.validate_system(system(*[{"input": f"virtual:{d}", "joystick": d} for d in ARROWS]))

    def test_a_system_binding_sending_two_things_fails(self):
        with self.assertRaises(ValueError):
            app_catalog.validate_system(system(*dpad(keys=[1], joystick="up")))
        with self.assertRaises(ValueError):
            app_catalog.validate_system(system(*dpad(keys=[1, 9])))

    def test_a_system_layer_binding_sending_two_things_fails(self):
        layered = system(*dpad(joystick="up"))
        layered["controller"]["layers"] = {"sixButtonPad": [{"input": "virtual:a", "keys": [1], "joystick": "button1"}]}
        with self.assertRaises(ValueError):
            app_catalog.validate_system(layered)


class GameDefaultsTest(unittest.TestCase):
    def validate(self, bindings, modifiers=frozenset()):
        record = {"title": "Game", "contentIds": ["sha256-fd-v1:" + "a" * 64],
                  "controller": {"defaults": {"withoutSticks": bindings}}}
        artwork = type("Artwork", (), {"expand": staticmethod(lambda value: value)})
        app_catalog.validator({"fd"}, artwork, modifiers=modifiers)(record)

    def test_a_game_binding_sending_two_things_fails(self):
        with self.assertRaises(ValueError):
            self.validate([{"input": "virtual:up", "joystick": "up", "keys": [70]}])
        with self.assertRaises(ValueError):
            self.validate([{"input": "virtual:x", "keys": [274, 32]}], DOS_MODIFIERS)

    def test_a_game_binding_sending_one_thing_passes(self):
        self.validate([{"input": "virtual:up", "joystick": "up"}, {"input": "virtual:y", "keys": [304, 47]}], DOS_MODIFIERS)


if __name__ == "__main__":
    unittest.main()
