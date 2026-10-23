"""Lossless identifiers for catalog artwork, with URL templates owned by code.

A LaunchBox image pair compacts to {"platform": code, "game": id, "boxArt"/"preview":
{"id": uuid, "format": 0 jpg | 1 png, "revision": 0 | 2 (r2_ variant)}}. Expanding it gives the
image URLs and their cache paths, art/catalog/<platform folder>/<game>/<box|screenshot>-<token>.webp.
Artwork from sources without an image server of their own is served from the app repository's
catalog/artwork at its own path; those records keep the path and URL as they are. A group can
mix the two: LaunchBox's image where it has one, and a self-hosted image ("boxArt": path,
"boxArtUrl": url) where it does not, so LaunchBox is always preferred.
"""
import copy
import hashlib
import re

BASE = "https://images.launchbox-app.com/"
UUID = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
URL = re.compile(re.escape(BASE) + r"(r2_)?(" + UUID.pattern + r")\.(jpg|png)")
KINDS = {"boxArt": "box", "preview": "screenshot"}
# Platform code in compact artwork -> cache folder. Codes are permanent once published.
PLATFORM_FOLDERS = {88: "pc88", 98: "pc98", 68: "x68000", 86: "fmtowns", 80: "msx", 83: "atarist"}


class References:
    """An app's artwork references: the platform codes its catalog uses and the repository that
    serves its self-hosted images."""

    def __init__(self, repo, platforms):
        self.platforms = tuple(platforms)
        assert all(code in PLATFORM_FOLDERS for code in self.platforms)
        self.self_hosted_base = f"https://raw.githubusercontent.com/MrJackSpade/{repo}/main/catalog/artwork/"
        folders = "|".join(re.escape(PLATFORM_FOLDERS[code]) for code in self.platforms)
        self.path = re.compile(r"art/catalog/(" + folders + r")/([1-9][0-9]*)/(box|screenshot)-([0-9a-f]{12})\.webp")
        self.code = {PLATFORM_FOLDERS[code]: code for code in self.platforms}
        self.self_hosted_path = re.compile(r"art/catalog/moby/([1-9][0-9]*)/(box|screenshot)-([0-9a-f]{12})\.webp")

    def self_hosted(self, art):
        """Whether every image is a self-hosted one with its own URL, all from one game."""
        games = set()
        for kind, label in KINDS.items():
            if kind not in art and kind + "Url" not in art:
                continue
            path = self.self_hosted_path.fullmatch(art.get(kind, ""))
            if not path or path[2] != label or art.get(kind + "Url") != self.self_hosted_base + path[0]:
                return False
            games.add(path[1])
        return len(games) == 1

    def expand(self, art):
        if not isinstance(art, dict):
            raise ValueError("invalid artwork")
        if not (art.keys() & {"platform", "game"}) and not any(isinstance(v, dict) for v in art.values()):
            return copy.deepcopy(art)
        if (not {"platform", "game"} < art.keys() or art.keys() - {"platform", "game", *KINDS, *(k + "Url" for k in KINDS)}
                or type(art["platform"]) is not int or art["platform"] not in self.platforms
                or type(art["game"]) is not int or not 1 <= art["game"] <= 2147483647):
            raise ValueError("invalid artwork group")
        result = {}
        for kind, label in KINDS.items():
            if kind not in art:
                continue
            ref = art[kind]
            if isinstance(ref, str):
                # An image LaunchBox lacks, served from the repository beside the group's others.
                path = self.self_hosted_path.fullmatch(ref)
                if not path or path[2] != label or art.get(kind + "Url") != self.self_hosted_base + ref:
                    raise ValueError("invalid self-hosted image in an artwork group")
                result[kind] = ref
                result[kind + "Url"] = art[kind + "Url"]
                continue
            if kind + "Url" in art:
                raise ValueError("a LaunchBox image has no URL field")
            if (not isinstance(ref, dict) or set(ref) != {"id", "format", "revision"}
                    or not isinstance(ref["id"], str) or not UUID.fullmatch(ref["id"])
                    or type(ref["format"]) is not int or ref["format"] not in (0, 1)
                    or type(ref["revision"]) is not int or ref["revision"] not in (0, 2)):
                raise ValueError("invalid artwork reference")
            url = BASE + ("r2_" if ref["revision"] == 2 else "") + ref["id"] + (".png" if ref["format"] else ".jpg")
            token = hashlib.sha256(url.encode("utf-8")).hexdigest()[:12]
            result[kind] = f'art/catalog/{PLATFORM_FOLDERS[art["platform"]]}/{art["game"]}/{label}-{token}.webp'
            result[kind + "Url"] = url
        return result

    def from_urls(self, platform, game, urls):
        """Compact artwork for one game's LaunchBox images, {"boxArt"|"preview": url}."""
        result = {"platform": platform, "game": game}
        for kind, url in urls.items():
            match = URL.fullmatch(url)
            if kind not in KINDS or not match:
                raise ValueError(f"not a LaunchBox image URL: {url}")
            result[kind] = {"id": match[2], "format": int(match[3] == "png"), "revision": 2 if match[1] else 0}
        self.expand(result)
        return result

    def compact(self, art):
        original = self.expand(art)
        if not original:
            return {}
        result, groups = {}, set()
        if original.keys() - {"boxArt", "preview", "boxArtUrl", "previewUrl"}:
            raise ValueError("unknown artwork fields")
        if self.self_hosted(original):
            return original
        for kind, label in KINDS.items():
            if kind not in original and kind + "Url" not in original:
                continue
            hosted = self.self_hosted_path.fullmatch(original.get(kind, ""))
            if hosted and hosted[2] == label and original.get(kind + "Url") == self.self_hosted_base + hosted[0]:
                result[kind] = original[kind]
                result[kind + "Url"] = original[kind + "Url"]
                continue
            path = self.path.fullmatch(original.get(kind, ""))
            url = URL.fullmatch(original.get(kind + "Url", ""))
            if not path or not url or path[3] != label:
                raise ValueError("artwork cannot be represented by catalog templates")
            groups.add((self.code[path[1]], int(path[2])))
            result[kind] = {"id": url[2], "format": int(url[3] == "png"), "revision": 2 if url[1] else 0}
        if len(groups) != 1:
            raise ValueError("artwork pair uses different asset groups")
        platform, game = groups.pop()
        result = {"platform": platform, "game": game, **result}
        if self.expand(result) != original:
            raise ValueError("artwork cache paths or URLs failed round-trip")
        return result
