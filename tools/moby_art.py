"""MobyGames artwork for catalog games LaunchBox has no cover for, served from the app's repository.

research.py gives a game with a MobyGames ID this artwork when LaunchBox has no cover for it
(LaunchBox screenshots alone are replaced too, since one game's pair comes from one source) and
MobyGames has a front cover or a screenshot. The cover is the platform's front cover, preferring
a Japanese release; the screenshot is the middle one captioned as play. Each is encoded as the
bundled LaunchBox images are (fetch_artwork.py) into catalog/artwork/art/catalog/moby/<game>/,
from the images shared/tools/mobygames.py downloaded; an image already encoded is kept.
catalog/research/moby-artwork-v1.json records each image's source URL and digest.

When some of those games' cover or screenshot lists are not dumped, nothing is encoded: their IDs
are written to .downloads/moby-art-needed.json for `mobygames.py art --games-from` and research
stops.
"""
import hashlib
import json
import re
import urllib.parse

from fetch_artwork import bundle
from mobygames import PLATFORMS

# Captions of screenshots that are not play.
NOT_GAMEPLAY = re.compile(r"title|menu|intro|opening|logo|credits|ending|staff|copyright|loading|rules|"
                          r"options|select|story|sign in|profile|start|ago", re.I)


def front_cover(covers):
    groups = sorted(covers["cover_groups"], key=lambda group: "Japan" not in group["countries"])
    return next((cover["image"] for group in groups for cover in group["covers"]
                 if cover["scan_of"] == "Front Cover"), None)


def gameplay_screenshot(screenshots):
    """The middle screenshot captioned as play, since MobyGames lists them in the order they
    come up and play is in the middle; otherwise the middle one."""
    shots = screenshots["screenshots"]
    play = [shot for shot in shots if not NOT_GAMEPLAY.search(shot.get("caption") or "")] or shots
    return play[len(play) // 2]["image"] if play else None


class MobyArt:
    def __init__(self, root, dump, platform, references):
        self.root = root
        self.dump = dump
        self.slug = platform
        self.platform = PLATFORMS[platform]
        self.references = references
        self.records = {}

    def _read(self, kind, game_id):
        return json.loads((self.dump / kind / f"{game_id}-{self.platform}.json").read_text(encoding="utf-8"))

    def require(self, game_ids):
        """Stop, naming the games to dump, unless every game's cover and screenshot lists are dumped."""
        missing = sorted(game_id for game_id in set(game_ids) for kind in ("covers", "screenshots")
                         if not (self.dump / kind / f"{game_id}-{self.platform}.json").exists())
        if missing:
            needed = self.root / ".downloads/moby-art-needed.json"
            needed.parent.mkdir(parents=True, exist_ok=True)
            needed.write_text(json.dumps({self.slug: sorted(set(missing))}), encoding="utf-8")
            raise SystemExit(f"{len(set(missing))} games' MobyGames artwork lists are not dumped; run "
                             f"shared/tools/mobygames.py art --platforms {self.slug} --games-from {needed} "
                             f"--root {self.dump}")

    def artwork(self, key, game_id):
        """The game's self-hosted artwork, or None when MobyGames has no image for it."""
        artwork = {}
        for field, kind, url in (("boxArt", "box", front_cover(self._read("covers", game_id))),
                                 ("preview", "screenshot", gameplay_screenshot(self._read("screenshots", game_id)))):
            if url is None:
                continue
            path = f"art/catalog/moby/{game_id}/{kind}-{hashlib.sha256(url.encode('utf-8')).hexdigest()[:12]}.webp"
            target = self.root / "catalog/artwork" / path
            if not target.is_file():
                source = self.dump / "images" / urllib.parse.urlsplit(url).path.lstrip("/")
                bundle(source.read_bytes(), target, url)
            artwork[field] = path
            artwork[field + "Url"] = self.references.self_hosted_base + path
            self.records[path] = {"game": key, "sourceUrl": url,
                                  "sha256": hashlib.sha256(target.read_bytes()).hexdigest()}
        return artwork or None

    def provenance(self):
        return {"schemaVersion": 1, "images": dict(sorted(self.records.items()))}
