import tempfile
import unittest
import zipfile
from pathlib import Path

from launchbox import extract, new_entry, page_url, parse_listing


class LaunchBoxTest(unittest.TestCase):
    def test_platform_listing_extracts_canonical_link(self):
        html = '<script>pages: 2</script><a class="list-item link-no-underline" href="/games/details/123-example">' \
               '<div><h3>Example &amp; More</h3></div></a>'
        pages, games = parse_listing(html)
        self.assertEqual(pages, 2)
        self.assertEqual(games, [{"id": "123", "title": "Example & More",
                                  "pageUrl": "https://gamesdb.launchbox-app.com/games/details/123-example"}])

    def test_media_prefers_original_cover_and_gameplay_then_uses_fallbacks(self):
        game = {"Name": "Example", "aliases": [], "images": [
            {"type": "Fanart - Box - Front", "url": "https://images.launchbox-app.com/fan.jpg"},
            {"type": "Screenshot - Game Title", "url": "https://images.launchbox-app.com/title.jpg"},
            {"type": "Box - Front", "url": "https://images.launchbox-app.com/box.jpg"},
            {"type": "Screenshot - Gameplay", "url": "https://images.launchbox-app.com/play.jpg"}]}
        entry = new_entry("pc98", "50001", game)
        self.assertEqual(entry["boxArtUrl"], "https://images.launchbox-app.com/box.jpg")
        self.assertEqual(entry["screenshotUrl"], "https://images.launchbox-app.com/play.jpg")
        fallback = new_entry("pc98", "50001", {**game, "images": game["images"][:2]})
        self.assertEqual(fallback["boxArtUrl"], "https://images.launchbox-app.com/fan.jpg")
        self.assertEqual(fallback["screenshotUrl"], "https://images.launchbox-app.com/title.jpg")
        selection = new_entry("pc98", "50001", {**game, "images": [
            {"type": "Screenshot - Game Select",
             "url": "https://images.launchbox-app.com/select.jpg"}]})
        self.assertEqual(selection["screenshotUrl"],
                         "https://images.launchbox-app.com/select.jpg")

    def test_verified_public_page_override(self):
        self.assertEqual(page_url("454686"),
                         "https://gamesdb.launchbox-app.com/games/details/"
                         "420466-daisenryaku-ii-campaign-version")
        self.assertEqual(page_url("454574"),
                         "https://gamesdb.launchbox-app.com/games/details/"
                         "420354-bio-100-free-game-collection")
        self.assertEqual(page_url("454575"),
                         "https://gamesdb.launchbox-app.com/games/details/"
                         "420355-bio-100-game-collection-part-2")
        self.assertEqual(page_url("454556"),
                         "https://gamesdb.launchbox-app.com/games/details/"
                         "420336-bakutotsu-turb")
        self.assertEqual(page_url("468902"),
                         "https://gamesdb.launchbox-app.com/games/details/"
                         "434684-abnormal-soldier")

    def test_454xxx_public_page_mapping(self):
        self.assertEqual(page_url("454484"),
                         "https://gamesdb.launchbox-app.com/games/details/420264")
        self.assertEqual(page_url("454554"),
                         "https://gamesdb.launchbox-app.com/games/details/420334")
        self.assertEqual(page_url("454997"),
                         "https://gamesdb.launchbox-app.com/games/details/420777")

    def test_462xxx_and_470xxx_public_page_mapping(self):
        self.assertEqual(page_url("462715"),
                         "https://gamesdb.launchbox-app.com/games/details/428495")
        self.assertEqual(page_url("462176"),
                         "https://gamesdb.launchbox-app.com/games/details/427956")
        self.assertEqual(page_url("470408"),
                         "https://gamesdb.launchbox-app.com/games/details/"
                         "436190-jewel-bem-hunter-lime-vol-02")
        self.assertEqual(page_url("470837"),
                         "https://gamesdb.launchbox-app.com/games/details/436621")
        self.assertEqual(page_url("470842"),
                         "https://gamesdb.launchbox-app.com/games/details/436626")
        self.assertEqual(page_url("457974"),
                         "https://gamesdb.launchbox-app.com/games/details/423754")
        self.assertEqual(page_url("459605"),
                         "https://gamesdb.launchbox-app.com/games/details/425385")
        self.assertEqual(page_url("465705"),
                         "https://gamesdb.launchbox-app.com/games/details/431487")
        self.assertEqual(page_url("468901"),
                         "https://gamesdb.launchbox-app.com/games/details/434683")
        self.assertEqual(page_url("453654"),
                         "https://gamesdb.launchbox-app.com/games/details/"
                         "418778-berserkers-front-gaiden-3")

    def test_extracts_only_target_platforms_and_source_linked_images(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "Metadata.zip"
            xml = """<LaunchBox><Game><DatabaseID>1</DatabaseID><Platform>NEC PC-8801</Platform>
                <Name>Example</Name><Overview>Text</Overview></Game>
                <Game><DatabaseID>2</DatabaseID><Platform>MS-DOS</Platform><Name>Other</Name></Game>
                <GameAlternateName><DatabaseID>1</DatabaseID><AlternateName>Alias</AlternateName></GameAlternateName>
                <GameImage><DatabaseID>1</DatabaseID><Type>Box - Front</Type>
                <FileName>image.png</FileName></GameImage></LaunchBox>"""
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr("Metadata.xml", xml)
            result = extract(path)
            self.assertEqual(set(result["pc88"]), {"1"})
            self.assertEqual(result["pc98"], {})
            self.assertEqual(result["pc88"]["1"]["aliases"], ["Alias"])
            self.assertEqual(result["pc88"]["1"]["images"][0]["url"],
                             "https://images.launchbox-app.com/image.png")


if __name__ == "__main__":
    unittest.main()
