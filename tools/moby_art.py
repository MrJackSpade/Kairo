"""MobyGames artwork for catalog games LaunchBox has no cover for, served from the app's repository.

research.py gives a game with a MobyGames ID this artwork when LaunchBox has no cover for it and
MobyGames has a front cover, or, for a game LaunchBox has no image of at all, a screenshot. (A
LaunchBox screenshot is then replaced too, since one game's pair comes from one source.) The cover is the platform's front cover, preferring
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


def front_cover(covers, rejected):
    groups = sorted(covers["cover_groups"], key=lambda group: "Japan" not in group["countries"])
    return next((cover["image"] for group in groups for cover in group["covers"]
                 if cover["scan_of"] == "Front Cover" and cover["image"] not in rejected), None)


def gameplay_screenshot(screenshots, rejected):
    """The middle screenshot captioned as play, since MobyGames lists them in the order they
    come up and play is in the middle; otherwise the middle one. Rejected ones are skipped."""
    shots = [shot for shot in screenshots["screenshots"] if shot["image"] not in rejected]
    play = [shot for shot in shots if not NOT_GAMEPLAY.search(shot.get("caption") or "")] or shots
    return play[len(play) // 2]["image"] if play else None


class MobyArt:
    def __init__(self, root, dump, platform, references, rejected):
        self.root = root
        self.dump = dump
        self.slug = platform
        self.platform = PLATFORMS[platform]
        self.references = references
        # Source image URLs the artwork review rejected (art_verdicts.py).
        self.rejected = rejected
        self.records = {}

    def _read(self, kind, game_id):
        return json.loads((self.dump / kind / f"{game_id}-{self.platform}.json").read_text(encoding="utf-8"))

    def _picks(self, game_id):
        return (front_cover(self._read("covers", game_id), self.rejected),
                gameplay_screenshot(self._read("screenshots", game_id), self.rejected))

    def _source(self, url):
        return self.dump / "images" / urllib.parse.urlsplit(url).path.lstrip("/")

    def _dumped(self, game_id):
        if not all((self.dump / kind / f"{game_id}-{self.platform}.json").exists() for kind in ("covers", "screenshots")):
            return False
        return all(self._source(url).is_file() for url in self._picks(game_id) if url is not None)

    def require(self, game_ids):
        """Stop, naming the games to dump, unless every game's cover and screenshot lists and the
        images chosen from them are dumped."""
        missing = sorted(game_id for game_id in set(game_ids) if not self._dumped(game_id))
        if missing:
            needed = self.root / ".downloads/moby-art-needed.json"
            needed.parent.mkdir(parents=True, exist_ok=True)
            needed.write_text(json.dumps({self.slug: missing}), encoding="utf-8")
            raise SystemExit(f"{len(missing)} games' MobyGames artwork is not dumped; run "
                             f"shared/tools/mobygames.py art --platforms {self.slug} --games-from {needed} "
                             f"--root {self.dump}")

    def artwork(self, key, game_id, cover_required):
        """The game's self-hosted artwork, or None when MobyGames has no image for it, or no cover
        when [cover_required] (the game has a LaunchBox screenshot to keep)."""
        picks = self._picks(game_id)
        if cover_required and picks[0] is None:
            return None
        artwork = {}
        for (field, kind), url in zip((("boxArt", "box"), ("preview", "screenshot")), picks):
            if url is None:
                continue
            path = f"art/catalog/moby/{game_id}/{kind}-{hashlib.sha256(url.encode('utf-8')).hexdigest()[:12]}.webp"
            target = self.root / "catalog/artwork" / path
            if not target.is_file():
                bundle(self._source(url).read_bytes(), target, url)
            artwork[field] = path
            artwork[field + "Url"] = self.references.self_hosted_base + path
            self.records[path] = {"game": key, "sourceUrl": url,
                                  "sha256": hashlib.sha256(target.read_bytes()).hexdigest()}
        return artwork or None

    def provenance(self):
        return {"schemaVersion": 1, "images": dict(sorted(self.records.items()))}
