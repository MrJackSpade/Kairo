import json
import tempfile
import unittest
from pathlib import Path

from research import match_title, moby_match, name_forms, parse_hint, relocate


def snapshot(*games):
    return {"atarist": {str(i): {"DatabaseID": str(i), "Name": name, "aliases": aliases, "ReleaseYear": str(year),
                                 "imageCounts": {}}
                        for i, (name, aliases, year) in enumerate(games, 1)}}


class ParseHintTest(unittest.TestCase):
    def test_version_tag_leaves_the_title(self):
        self.assertEqual(parse_hint("Dungeon Master v1.2 (1988)(FTL)(de)[!].zip")[:2], ("Dungeon Master", 1988))
        self.assertEqual(parse_hint("Railroad Tycoon v755.01 (1991)(MicroProse).zip")[0], "Railroad Tycoon")

    def test_moved_articles_return_to_the_front(self):
        self.assertEqual(parse_hint("Corporation, The (1990)(Core Design).zip")[0], "The Corporation")
        self.assertEqual(parse_hint("Magazin, Das (1989)(Ariolasoft)(de).zip")[0], "Das Magazin")
        self.assertEqual(parse_hint("Avventura, L' (1990)(-).zip")[0], "L'Avventura")

    def test_a_title_ending_in_a_roman_numeral_keeps_it(self):
        self.assertEqual(parse_hint("Ultima V (1988)(Origin).zip")[0], "Ultima V")


class MatchTitleTest(unittest.TestCase):
    def test_part_and_roman_numerals_are_spellings(self):
        games = snapshot(("Back to the Future Part III", [], 1991), ("Street Fighter II", [], 1992))
        self.assertEqual(match_title("Back To The Future III", 1991, ["atarist"], games), "atarist:1")
        self.assertEqual(match_title("Street Fighter 2", 1992, ["atarist"], games), "atarist:2")

    def test_whole_name_beats_a_name_without_its_subtitle(self):
        games = snapshot(("Populous", [], 1989), ("Populous: The Promised Lands", [], 1989))
        self.assertEqual(match_title("Populous", 1989, ["atarist"], games), "atarist:1")

    def test_two_subtitled_names_stay_ambiguous(self):
        games = snapshot(("A320 Airbus: Edition USA", [], 1991), ("A320 Airbus: Edition Europa", [], 1991))
        self.assertIsNone(match_title("A320 Airbus", 1991, ["atarist"], games))

    def test_years_must_agree(self):
        games = snapshot(("Strike Force Harrier", ["High Roller"], 1986))
        self.assertIsNone(match_title("High Roller", 1992, ["atarist"], games))
        self.assertEqual(match_title("High Roller", 1986, ["atarist"], games), "atarist:1")


class MobyMatchTest(unittest.TestCase):
    def names(self, *games):
        found = {}
        for key, name, year in games:
            for form in name_forms(name):
                found.setdefault(form, set()).add((key, year))
        return found

    def test_an_alternate_title_finds_its_game(self):
        names = self.names(("atarist:1", "Bad Dudes vs. Dragon Ninja", 1988), ("atarist:1", "Dragon Ninja", 1988))
        self.assertEqual(moby_match(names, "Dragon Ninja", 1988), "atarist:1")
        self.assertIsNone(moby_match(names, "Dragon Ninja", 1993))

    def test_a_name_two_games_share_matches_neither(self):
        names = self.names(("atarist:1", "Star Wars", 1987), ("atarist:moby-2", "Star Wars", 1987))
        self.assertIsNone(moby_match(names, "Star Wars", 1987))


class RelocateTest(unittest.TestCase):
    def test_records_follow_a_local_game_to_the_game_it_joined(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "descriptions-v1.json"
            path.write_text(json.dumps({"descriptions": {"x:local-1": "text", "x:2": "kept"}}), encoding="utf-8")
            relocate(path, "descriptions", {"x:local-1": "x:3"}, {"x:2": {}, "x:3": {}})
            self.assertEqual(json.loads(path.read_text(encoding="utf-8"))["descriptions"],
                             {"x:3": "text", "x:2": "kept"})

    def test_a_game_with_its_own_record_is_a_conflict(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "descriptions-v1.json"
            path.write_text(json.dumps({"descriptions": {"x:local-1": "a", "x:3": "b"}}), encoding="utf-8")
            with self.assertRaises(SystemExit):
                relocate(path, "descriptions", {"x:local-1": "x:3"}, {"x:3": {}})


if __name__ == "__main__":
    unittest.main()
