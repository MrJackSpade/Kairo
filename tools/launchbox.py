#!/usr/bin/env python3
"""LaunchBox Games Database research shared by every Kairo app.

Commands:
  snapshot  Stream chosen platforms' records out of LaunchBox's Metadata.zip into ignored JSON.
  listing   Cache a platform's public listing pages and their direct game-page URLs.
  enrich    Fill missing box art and screenshot URLs in a match catalog from cached game pages.

Platforms are named by Kairo slugs (see PLATFORMS). None of these commands download images or
game files; image URLs are recorded for the apps' own artwork download flow.
"""

import argparse
import difflib
import html
import json
import re
import subprocess
import time
import unicodedata
import zipfile
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlparse
from xml.etree import ElementTree as ET

# Kairo platform slug -> LaunchBox platform name in Metadata.xml.
PLATFORMS = {
    "pc88": "NEC PC-8801",
    "pc98": "NEC PC-9801",
    "x68000": "Sharp X68000",
    "fmtowns": "Fujitsu FM Towns Marty",
    "msx": "Microsoft MSX",
    "msx2": "Microsoft MSX2",
    "msx2plus": "Microsoft MSX2+",
}
# Kairo platform slug -> public website listing slug, for the platforms whose listings are scraped.
SITE_SLUGS = {
    "pc88": "192-nec-pc-8801",
    "pc98": "193-nec-pc-9801",
}
SNAPSHOT_FIELDS = ("Name", "Overview", "Developer", "Publisher", "ReleaseDate", "Platform")
IMAGE_BASE = "https://images.launchbox-app.com/"
SITE = "https://gamesdb.launchbox-app.com"

BOX_TYPES = ("Box - Front", "Fanart - Box - Front")
SCREENSHOT_TYPES = ("Screenshot - Gameplay", "Screenshot - Game Title",
                    "Screenshot - Game Select")


# --- Metadata.zip snapshot ---------------------------------------------------------------

def extract(source, slugs=("pc88", "pc98")):
    """{slug: {DatabaseID: record}} for the named platforms, with aliases and image URLs."""
    wanted = {PLATFORMS[slug]: slug for slug in slugs}
    result = {slug: {} for slug in slugs}
    with zipfile.ZipFile(source) as archive, archive.open("Metadata.xml") as stream:
        iterator = ET.iterparse(stream, events=("start", "end"))
        _, root = next(iterator)
        for event, node in iterator:
            if event != "end":
                continue
            if node.tag == "Game":
                platform = node.findtext("Platform")
                game_id = node.findtext("DatabaseID")
                if platform in wanted and game_id:
                    game = {field: node.findtext(field) or "" for field in SNAPSHOT_FIELDS}
                    game.update(DatabaseID=game_id, aliases=[], images=[])
                    result[wanted[platform]][game_id] = game
                root.clear()
            elif node.tag == "GameAlternateName":
                game_id = node.findtext("DatabaseID")
                name = node.findtext("AlternateName")
                for games in result.values():
                    if game_id in games and name:
                        games[game_id]["aliases"].append(name)
                root.clear()
            elif node.tag == "GameImage":
                game_id = node.findtext("DatabaseID")
                filename = node.findtext("FileName") or ""
                category = node.findtext("Type") or "Other"
                if filename and "/" not in filename and "\\" not in filename:
                    for games in result.values():
                        if game_id in games:
                            games[game_id]["images"].append({
                                "type": category, "url": IMAGE_BASE + filename})
                root.clear()
    for games in result.values():
        for game in games.values():
            game["aliases"] = sorted(set(game["aliases"]))
            game["imageCounts"] = dict(Counter(image["type"] for image in game["images"]))
    return result


def load_metadata(path):
    """{DatabaseID: record} from a snapshot JSON or an extracted Metadata.xml (every platform)."""
    if Path(path).suffix.lower() == ".json":
        return json.loads(Path(path).read_text(encoding="utf-8"))
    games = {}
    aliases = defaultdict(list)
    images = defaultdict(lambda: Counter())
    for _, node in ET.iterparse(path, events=("end",)):
        if node.tag == "Game":
            game_id = node.findtext("DatabaseID")
            if game_id:
                games[game_id] = {tag: node.findtext(tag) or "" for tag in SNAPSHOT_FIELDS}
                games[game_id]["DatabaseID"] = game_id
            node.clear()
        elif node.tag == "GameAlternateName":
            aliases[node.findtext("DatabaseID")].append(node.findtext("AlternateName") or "")
            node.clear()
        elif node.tag == "GameImage":
            images[node.findtext("DatabaseID")][node.findtext("Type") or "Other"] += 1
            node.clear()
    for game_id, game in games.items():
        game["aliases"] = sorted(set(aliases[game_id]))
        game["imageCounts"] = dict(images[game_id])
    return games


# --- Title matching ----------------------------------------------------------------------

NON_WORD = re.compile(r"[^\w]+", re.UNICODE)


def key(value):
    """A title's comparison form: NFKC, case-folded, letters and digits only."""
    value = unicodedata.normalize("NFKC", value).casefold()
    return NON_WORD.sub("", value)


def candidates(games, title, limit=8):
    """The snapshot records whose name or an alias best resembles a title."""
    sought = key(title)
    ranked = []
    for game in games.values():
        names = [game["Name"], *game["aliases"]]
        score = max((1.0 if key(name) == sought else
                     difflib.SequenceMatcher(None, sought, key(name)).ratio()) for name in names)
        if score >= 0.46:
            ranked.append((score, game))
    ranked.sort(key=lambda pair: (-pair[0], pair[1]["Name"], pair[1]["DatabaseID"]))
    return [{"score": round(score, 3), "DatabaseID": game["DatabaseID"],
             "Name": game["Name"], "aliases": game["aliases"],
             "imageCounts": game["imageCounts"]} for score, game in ranked[:limit]]


# --- Game identity and media -------------------------------------------------------------

def page_url(game_id):
    """The public game page of a Metadata.zip DatabaseID."""
    # These snapshot records resolve to different public IDs after LaunchBox
    # database changes; their URLs were checked against official pages or
    # official listing links on 2026-09-23.
    overrides = {
        "450780": "412248-battle-block-alfin",
        "453654": "418778-berserkers-front-gaiden-3",
        "454555": "420335-poy",
        "454556": "420336-bakutotsu-turb",
        "454557": "420337-roli-roli-rolling",
        "454559": "420339-seena-2",
        "454560": "420340-sengoku-turb",
        "454562": "420342-stardust-chaser",
        "454564": "420344-super-depth",
        "454566": "420346-super-depth-2-finalty",
        "454567": "420347-turb-3",
        "454569": "420349-twins",
        "454570": "420350-turb",
        "454571": "420351-turb-2",
        "454572": "420352-twins-2",
        "454574": "420354-bio-100-free-game-collection",
        "454575": "420355-bio-100-game-collection-part-2",
        "454683": "420463-camel-zoo",
        "454686": "420466-daisenryaku-ii-campaign-version",
        "454691": "420471-car-ii-grand-prix",
        "468902": "434684-abnormal-soldier",
        "470408": "436190-jewel-bem-hunter-lime-vol-02",
        "470410": "436192-jewel-bem-hunter-lime-vol03",
        "470413": "436195-jewel-bem-hunter-lime-vol-04",
        "470421": "436203-lightning-warrior-raidy",
        "475938": "441727-the-tower-of-zarbartz",
    }
    if game_id in overrides:
        return f"{SITE}/games/details/" + overrides[game_id]
    # The adjacent 453654-453669 records tentatively follow the offset
    # confirmed for Berserkers Front Gaiden 3 (453654). Official pages
    # sampled across the 454xxx-462xxx snapshot blocks resolve
    # at DatabaseID - 34220. Samples in the 465xxx-470xxx block resolve at
    # DatabaseID - 34218, except Jewel Bem Hunter Lime volumes 05-12
    # (470837-470844), which resolve at DatabaseID - 34216. Remaining records
    # in those blocks still need final catalog review.
    snapshot_id = int(game_id)
    if 453654 <= snapshot_id <= 453669:
        public_id = snapshot_id - 34876
    elif 454000 <= snapshot_id < 463000:
        public_id = snapshot_id - 34220
    elif 470837 <= snapshot_id <= 470844:
        public_id = snapshot_id - 34216
    elif 465000 <= snapshot_id < 471000:
        public_id = snapshot_id - 34218
    else:
        public_id = snapshot_id - 50000
    if public_id <= 0:
        raise ValueError(f"no public page mapping for DatabaseID {game_id}")
    return f"{SITE}/games/details/{public_id}"


def first_image(game, categories):
    """The first image URL of the earliest listed category the snapshot record has."""
    for category in categories:
        url = next((image["url"] for image in game["images"]
                    if image["type"] == category), "")
        if url:
            return url
    return ""


def new_entry(platform, game_id, game):
    """A match-catalog entry for a snapshot record, before any disks are linked."""
    return {
        "platform": platform, "databaseId": game_id,
        "title": game["Name"], "aliases": list(game["aliases"]),
        "sourceGroups": [], "contentIds": [], "description": "",
        "pageUrl": page_url(game_id),
        "boxArtUrl": first_image(game, BOX_TYPES),
        "screenshotUrl": first_image(game, SCREENSHOT_TYPES),
    }


# --- Public website: listings and game pages ---------------------------------------------

LISTING_LINK = re.compile(r'<a class="list-item link-no-underline" href="(/games/details/(\d+)[^"]*)">')
LISTING_TITLE = re.compile(r"<h3\b[^>]*>(.*?)</h3>", re.DOTALL)
PAGE_COUNT = re.compile(r"\bpages:\s*(\d+)")
TAG = re.compile(r"<[^>]+>")
PAGE_ID = re.compile(r"^/games/details/(\d+)(?:-[^/]*)?$")
PAGE_TITLE = re.compile(r"<title>(.*?) - LaunchBox Games Database</title>", re.DOTALL)
SECTION = re.compile(r"<article\b[^>]*>\s*<h3\b[^>]*>(.*?)</h3>(.*?)</article>", re.DOTALL)
IMAGE = re.compile(r'<img\b[^>]*\bsrc="(https://(?:images\.launchbox-app\.com|gamesdb-images\.launchbox\.gg)/+[^\"]+)"', re.DOTALL)


def parse_listing(source):
    pages = PAGE_COUNT.search(source)
    if not pages:
        raise ValueError("listing has no pagination count")
    entries = {}
    for link in LISTING_LINK.finditer(source):
        heading = LISTING_TITLE.search(source, link.end(), link.end() + 2200)
        if not heading:
            raise ValueError("game link has no nearby title: " + link.group(1))
        title = html.unescape(TAG.sub("", heading.group(1))).strip()
        if not title:
            raise ValueError("game link has empty title: " + link.group(1))
        game_id = link.group(2)
        entry = {"id": game_id, "title": title, "pageUrl": SITE + html.unescape(link.group(1))}
        if game_id in entries and entries[game_id] != entry:
            raise ValueError("conflicting listing for game " + game_id)
        entries[game_id] = entry
    if not entries:
        raise ValueError("listing contains no game links")
    return int(pages.group(1)), list(entries.values())


def download(url):
    # The bundled Android-toolchain Python lacks SSL support on Windows.
    result = subprocess.run(
        ["curl.exe", "-L", "--fail", "--silent", "--show-error", "--retry", "2",
         "--max-time", "40", "-A", "Kairo catalog research (public platform pages)", url],
        check=True, capture_output=True,
    )
    return result.stdout.decode("utf-8")


def scrape_listing(platform, cache_dir, refresh=False, delay=0.5):
    base_url = f"{SITE}/platforms/games/{SITE_SLUGS[platform]}"
    entries = {}
    page = 1
    page_count = None
    while page_count is None or page <= page_count:
        path = cache_dir / f"{platform}-page-{page:02d}.html"
        if refresh or not path.exists():
            if page > 1:
                time.sleep(delay)
            url = base_url if page == 1 else f"{base_url}/page/{page}"
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(download(url), encoding="utf-8")
        count, games = parse_listing(path.read_text(encoding="utf-8"))
        if page_count is None:
            page_count = count
        elif count != page_count:
            raise ValueError(f"page count changed on page {page}: {count} vs {page_count}")
        for game in games:
            if game["id"] in entries:
                raise ValueError(f"game {game['id']} occurs on multiple pages")
            entries[game["id"]] = game
        print(f"{platform} page {page}/{page_count}: {len(games)} games", flush=True)
        page += 1
    return {
        "sourceUrl": base_url,
        "retrievedUtc": datetime.now(timezone.utc).isoformat(),
        "platform": platform,
        "pages": page_count,
        "entries": sorted(entries.values(), key=lambda game: (game["title"].casefold(), int(game["id"]))),
    }


def page_id(url):
    parsed = urlparse(url)
    match = PAGE_ID.fullmatch(parsed.path)
    if parsed.scheme != "https" or parsed.netloc != "gamesdb.launchbox-app.com" or not match:
        raise ValueError(f"unexpected game page URL: {url}")
    return match.group(1)


def parse_page(source, expected_title):
    title = PAGE_TITLE.search(source)
    if not title or html.unescape(title.group(1)).strip() != expected_title.strip():
        raise ValueError(f"page title differs from catalog title: {expected_title}")
    sections = {}
    for section in SECTION.finditer(source):
        category = html.unescape(re.sub(r"<[^>]*>", "", section.group(1))).strip()
        image = IMAGE.search(section.group(2))
        if image:
            sections.setdefault(category, html.unescape(image.group(1)).replace(
                IMAGE_BASE + "/", IMAGE_BASE))
    return {
        "boxArtUrl": next((sections[kind] for kind in BOX_TYPES if kind in sections), ""),
        "screenshotUrl": next((sections[kind] for kind in SCREENSHOT_TYPES if kind in sections), ""),
    }


def fetch_page(url, destination):
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_name(destination.name + ".part")
    result = subprocess.run(
        ["curl.exe", "-L", "--fail", "--silent", "--show-error", "--retry", "2",
         "--connect-timeout", "15", "--max-time", "45", "-o", str(temporary), url],
        capture_output=True, text=True,
    )
    if result.returncode or not temporary.exists() or not temporary.stat().st_size:
        temporary.unlink(missing_ok=True)
        raise RuntimeError(result.stderr.strip() or "empty page response")
    temporary.replace(destination)


def enrich(catalog, cache_dir, limit=None, delay=0.1, all_pages=False):
    """Fill missing boxArtUrl/screenshotUrl of match entries from their cached game pages."""
    fetched = filled = cached = 0
    errors = []
    for entry in catalog["entries"]:
        missing = [field for field in ("boxArtUrl", "screenshotUrl") if not entry[field]]
        if not missing and not all_pages:
            continue
        path = cache_dir / f"game-{page_id(entry['pageUrl'])}.html"
        if not path.is_file():
            if limit is not None and fetched >= limit:
                continue
            try:
                fetch_page(entry["pageUrl"], path)
                fetched += 1
                if delay:
                    time.sleep(delay)
            except (OSError, RuntimeError) as error:
                errors.append({"pageUrl": entry["pageUrl"], "error": str(error)})
                continue
        else:
            cached += 1
        try:
            media = parse_page(path.read_text(encoding="utf-8"), entry["title"])
        except (OSError, ValueError) as error:
            errors.append({"pageUrl": entry["pageUrl"], "error": str(error)})
            continue
        for field in missing:
            if media[field]:
                entry[field] = media[field]
                filled += 1
        if (fetched + cached) % 25 == 0:
            print(f"pages {fetched} fetched, {cached} cached; {filled} media URLs filled; "
                  f"{len(errors)} errors", flush=True)
    print(f"Done: {fetched} pages fetched, {cached} cached; {filled} media URLs filled; "
          f"{len(errors)} errors", flush=True)
    return errors


# --- Command line ------------------------------------------------------------------------

def write_json(path, value, **options):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    temporary.write_text(json.dumps(value, ensure_ascii=False, **options) + "\n", encoding="utf-8")
    temporary.replace(path)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command", required=True)
    snapshot = commands.add_parser("snapshot", help="extract platforms from Metadata.zip")
    snapshot.add_argument("source", type=Path)
    snapshot.add_argument("output_directory", type=Path)
    snapshot.add_argument("platforms", nargs="+", choices=PLATFORMS)
    listing = commands.add_parser("listing", help="cache a platform's public listing")
    listing.add_argument("platform", choices=SITE_SLUGS)
    listing.add_argument("output", type=Path)
    listing.add_argument("--cache-dir", type=Path, default=Path(".downloads/launchbox/platform-pages"))
    listing.add_argument("--refresh", action="store_true")
    media = commands.add_parser("enrich", help="fill missing media URLs from game pages")
    media.add_argument("catalog", type=Path)
    media.add_argument("cache_dir", type=Path)
    media.add_argument("output", type=Path)
    media.add_argument("--limit", type=int, help="fetch at most this many new pages")
    media.add_argument("--delay", type=float, default=0.1)
    media.add_argument("--all-pages", action="store_true",
                       help="cache and check every matched game page, including entries with media")
    args = parser.parse_args()
    if args.command == "snapshot":
        for platform, games in extract(args.source, args.platforms).items():
            destination = args.output_directory / f"{platform}-snapshot.json"
            write_json(destination, games, sort_keys=True)
            print(f"{platform}: {len(games)} games -> {destination}")
    elif args.command == "listing":
        result = scrape_listing(args.platform, args.cache_dir, args.refresh)
        write_json(args.output, result, indent=2)
        print(f"Saved {len(result['entries'])} {args.platform} games to {args.output}")
    else:
        catalog = json.loads(args.catalog.read_text(encoding="utf-8"))
        errors = enrich(catalog, args.cache_dir, args.limit, args.delay, args.all_pages)
        write_json(args.output, catalog, indent=2)
        write_json(args.cache_dir / "media-page-errors.json", errors, indent=2)


if __name__ == "__main__":
    main()
