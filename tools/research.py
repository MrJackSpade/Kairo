#!/usr/bin/env python3
"""Assemble an app's catalog research from LaunchBox, MobyGames, media scans and decisions.

Usage: research.py <app root> --mobygames <dump directory>

Writes catalog/research/games-v1.json for tools/app_catalog.py. The app's tools/catalog_config.py
names its PLATFORMS (LaunchBox slugs), MOBYGAMES (its MobyGames listing slug) and SCANS:
{set name: (scan index path, LaunchBox slugs to match against, in order)}. An optional
GROUP(archive path) names the game an archive belongs to (default: its file name); archives of
one group share a title hint and, when unmatched, one local game. An optional IGNORE(archive path)
marks archives that are not games, such as firmware; they are decided "ignore".

Decisions live beside the output and are kept across runs; new items are decided automatically
and the decision is recorded so it can be reviewed and corrected:
  media-matches-v1.json  {"<set>:<archive path>": "<game key>" | "local" | "ignore"}; an archive
                         nothing matches is local without a recorded decision
  moby-matches-v1.json   {"<MobyGames ID>": "<game key>" | "own"}
  descriptions-v1.json   {"<game key>": "original description"}
  overrides-v1.json      {"<game key>": {"title"|"aliases"|"adult"|"artwork": ...}} applied last
Every adult game is added to catalog/core-review-v1.json's exclusions if it is not there yet. A
medium found in several games' sets identifies none of them: it is listed under sharedContentIds.

An archive matches a LaunchBox game when its file name's title equals the game's name or an
alias and the years agree, or when the closest fuzzy title scores at least 0.93 with agreeing
years, the same sequel numbers and no tie. Failing that, it joins a MobyGames-only game of exactly its title. Anything else
becomes a "local" game titled from its group.
"""

import argparse
import hashlib
import importlib.util
import json
import re
from pathlib import Path

from launchbox import BOX_TYPES, SCREENSHOT_TYPES, candidates, key as title_key
from launchbox_artwork import URL

YEAR = re.compile(r"(?:19|20)[0-9]{2}")
FUZZY_MINIMUM = 0.93


def load_config(root):
    spec = importlib.util.spec_from_file_location("catalog_config", root / "tools/catalog_config.py")
    config = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(config)
    return config


def read(path, default):
    return json.loads(path.read_text(encoding="utf-8")) if path.is_file() else default


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=1, sort_keys=True) + "\n", encoding="utf-8")
    temporary.replace(path)


def parse_hint(name):
    """(title, year or None, publisher or None) from "Title (1986) (Publisher) (J).ext"-style names."""
    stem = Path(name).name
    stem = re.sub(r"\.[A-Za-z0-9]{1,4}$", "", stem)
    stem = re.sub(r"\[[^]]*]", "", stem).strip()
    title = re.split(r"\s*\(", stem, maxsplit=1)[0].strip(" -_") or stem
    groups = [group.strip() for group in re.findall(r"\(([^)]*)\)", stem)]
    year = next((int(group[:4]) for group in groups if YEAR.match(group)), None)
    publisher = next((group for group in groups if not re.match(r"(?:19|20)[0-9x]{2}", group)), None)
    return title, year, publisher


def year_of(game):
    date = game.get("ReleaseDate") or ""
    return int(date[:4]) if date[:4].isdigit() else None


def years_agree(a, b):
    return a is None or b is None or a == b


def first_url(game, categories):
    """The first LaunchBox image of the earliest category whose URL compacts to an artwork reference."""
    for category in categories:
        for image in game["images"]:
            if image["type"] == category and URL.fullmatch(image["url"]):
                return image["url"]
    return ""


def launchbox_entry(slug, game):
    entry = {"title": game["Name"], "launchbox": game["DatabaseID"]}
    if game["aliases"]:
        entry["aliases"] = [alias for alias in game["aliases"] if alias != game["Name"]]
    for field, source in (("developer", "Developer"), ("publisher", "Publisher")):
        if game.get(source):
            entry[field] = game[source]
    if year_of(game):
        entry["year"] = year_of(game)
    for field, categories in (("boxArtUrl", BOX_TYPES), ("screenshotUrl", SCREENSHOT_TYPES)):
        url = first_url(game, categories)
        if url:
            entry[field] = url
    return entry


ROMAN = {"II": "2", "III": "3", "IV": "4", "V": "5", "VI": "6", "VII": "7", "VIII": "8", "IX": "9", "X": "10"}


def sequel_numbers(title):
    """A title's standalone numbers, Roman numerals read as numbers and a lone 1 as none, so
    "Hydlide 2" does not match "Hydlide" and "Sangokusi 2" still matches "Sangokushi II"."""
    tokens = re.findall(r"[0-9]+|\b[IVX]{1,4}\b", title)
    return {ROMAN.get(token, token.lstrip("0") or "0") for token in tokens} - {"1"}


def match_title(title, year, slugs, snapshots):
    """The one LaunchBox game key a title names, searching platforms in order, or None."""
    sought = title_key(title)
    for slug in slugs:
        games = snapshots[slug]
        exact = [game_id for game_id, game in games.items()
                 if sought in {title_key(name) for name in (game["Name"], *game["aliases"])}
                 and years_agree(year, year_of(game))]
        if len(exact) == 1:
            return f"{slug}:{exact[0]}"
        if len(exact) > 1:
            return None
    ranked = [(candidate["score"], slug, candidate["DatabaseID"]) for slug in slugs
              for candidate in candidates(snapshots[slug], title, limit=3)
              if years_agree(year, year_of(snapshots[slug][candidate["DatabaseID"]]))
              and any(sequel_numbers(name) == sequel_numbers(title)
                      for name in (candidate["Name"], *candidate["aliases"]))]
    ranked.sort(key=lambda item: -item[0])
    if ranked and ranked[0][0] >= FUZZY_MINIMUM and (len(ranked) == 1 or ranked[1][0] < ranked[0][0]):
        return f"{ranked[0][1]}:{ranked[0][2]}"
    return None


def local_key(slug, archive):
    return f"{slug}:local-" + hashlib.sha1(archive.encode("utf-8")).hexdigest()[:10]


def assemble(root, moby_root):
    config = load_config(root)
    research = root / "catalog/research"
    snapshots = {slug: read(root / f".downloads/launchbox/{slug}-snapshot.json", None) for slug in config.PLATFORMS}
    missing = [slug for slug, snapshot in snapshots.items() if snapshot is None]
    if missing:
        raise SystemExit(f"missing LaunchBox snapshots: {missing}")
    games = {f"{slug}:{game_id}": launchbox_entry(slug, game)
             for slug, snapshot in snapshots.items() for game_id, game in snapshot.items()}

    # MobyGames: link each listed game to its LaunchBox game, or give it its own entry.
    moby_decisions = read(research / "moby-matches-v1.json", {"schemaVersion": 1, "games": {}})
    listing = json.loads((moby_root / f"{config.MOBYGAMES}-listing.json").read_text(encoding="utf-8"))
    for game in listing:
        moby_id = str(game["id"])
        decision = moby_decisions["games"].get(moby_id)
        if decision is None:
            decision = match_title(game["title"], game.get("release_year"), config.PLATFORMS, snapshots) or "own"
            moby_decisions["games"][moby_id] = decision
        adult = "Adult" in game.get("genres", [])
        if decision == "own":
            key = f"{config.PLATFORMS[0]}:moby-{moby_id}"
            games[key] = {"title": game["title"], "mobygames": game["id"]}
            if game.get("release_year"):
                games[key]["year"] = game["release_year"]
        else:
            key = decision
            if key not in games:
                raise SystemExit(f"MobyGames {moby_id} names unknown game {key}")
            games[key]["mobygames"] = game["id"]
        if adult:
            games[key]["adult"] = True

    # Media: every scanned archive belongs to a game, its own local game, or nothing.
    media_decisions = read(research / "media-matches-v1.json", {"schemaVersion": 1, "archives": {}})
    relinked = {f"{config.PLATFORMS[0]}:moby-{moby_id}": decision
                for moby_id, decision in moby_decisions["games"].items() if decision != "own"}
    own = [(key, entry) for key, entry in games.items() if ":moby-" in key]
    titles = [title_key(entry["title"]) for _, entry in own]
    # A MobyGames-only game whose title no other MobyGames-only game shares.
    own_moby = {title_key(entry["title"]): key for key, entry in own if titles.count(title_key(entry["title"])) == 1}
    group_of = getattr(config, "GROUP", lambda path: path.rsplit("/", 1)[-1])
    ignored = getattr(config, "IGNORE", lambda path: False)
    for set_name, (index_path, slugs) in config.SCANS.items():
        index = read(root / index_path, None)
        if index is None:
            raise SystemExit(f"missing media scan {index_path}")
        for archive in index["archives"]:
            if archive["status"] != "ready":
                continue
            name = f"{set_name}:{archive['path']}"
            group = group_of(archive["path"])
            title, year, _ = parse_hint(group)
            decision = media_decisions["archives"].get(name)
            if decision is None:
                decision = ("ignore" if ignored(archive["path"]) else
                            match_title(title, year, slugs, snapshots) or own_moby.get(title_key(title)))
                if decision is not None:
                    media_decisions["archives"][name] = decision
                else:
                    # Recomputed each run, so a later match replaces it; local keys are stable.
                    decision = "local"
            if decision == "ignore":
                continue
            # A MobyGames-only game linked to its LaunchBox game since this was decided.
            decision = relinked.get(decision, decision)
            key = local_key(slugs[0], f"{set_name}:{group}") if decision == "local" else decision
            if key not in games:
                if decision != "local":
                    raise SystemExit(f"{name} names unknown game {key}")
                games[key] = {"title": title}
                if year:
                    games[key]["year"] = year
            entry = games[key]
            entry.setdefault("sourceGroups", []).append(name)
            entry["contentIds"] = sorted(set(entry.get("contentIds", [])) |
                                         {disk["contentId"] for disk in archive["disks"]})

    # A medium found in several games' sets (a blank user disk, a series' shared data disk)
    # identifies none of them; it is left out and listed instead.
    owners = {}
    for key, entry in games.items():
        for content_id in entry.get("contentIds", []):
            owners.setdefault(content_id, []).append(key)
    shared = {content_id: sorted(keys) for content_id, keys in sorted(owners.items()) if len(keys) > 1}
    for key, entry in games.items():
        if "contentIds" in entry:
            entry["contentIds"] = [content_id for content_id in entry["contentIds"] if content_id not in shared]
            if not entry["contentIds"]:
                del entry["contentIds"]

    descriptions = read(research / "descriptions-v1.json", {"schemaVersion": 1, "descriptions": {}})["descriptions"]
    unknown = descriptions.keys() - games.keys()
    if unknown:
        raise SystemExit(f"descriptions for unknown games: {sorted(unknown)[:5]}")
    for key, text in descriptions.items():
        games[key]["description"] = text
    overrides = read(research / "overrides-v1.json", {"schemaVersion": 1, "games": {}})["games"]
    for key, fields in overrides.items():
        if key not in games or fields.keys() - {"title", "aliases", "adult", "artwork"}:
            raise SystemExit(f"invalid override for {key}")
        if "title" in fields and fields["title"] != games[key]["title"]:
            # The source name stays findable when a reviewed title replaces it.
            games[key]["aliases"] = sorted({*games[key].get("aliases", []), games[key]["title"]})
        games[key].update({field: value for field, value in fields.items() if field != "aliases"})
        if "aliases" in fields:
            games[key]["aliases"] = fields["aliases"]
        if "artwork" in fields:
            games[key].pop("boxArtUrl", None)
            games[key].pop("screenshotUrl", None)
    for entry in games.values():
        if "sourceGroups" in entry:
            entry["sourceGroups"] = sorted(entry["sourceGroups"])

    # Adult games leave the core review; existing exclusions and their reasons are kept.
    review_path = root / "catalog/core-review-v1.json"
    review = read(review_path, {"schemaVersion": 1, "excluded": {}, "approvedArtwork": {}})
    for old_key in [key for key in review["excluded"] if key in relinked]:
        # The exclusion follows a MobyGames-only game to the LaunchBox game it was linked to.
        review["excluded"].setdefault(relinked[old_key], review["excluded"][old_key])
        del review["excluded"][old_key]
    for key, entry in sorted(games.items()):
        if entry.get("adult") and key not in review["excluded"]:
            reason = ("MobyGames Adult genre" if "mobygames" in entry else "Adult-content review")
            review["excluded"][key] = {"title": entry["title"], "reason": reason}
    write(review_path, review)
    write(research / "moby-matches-v1.json", moby_decisions)
    write(research / "media-matches-v1.json", media_decisions)
    write(research / "games-v1.json", {"schemaVersion": 1, "games": games, "sharedContentIds": shared})
    decided = media_decisions["archives"].values()
    print(f"{config.APP}: {len(games)} games; archives: {sum(v not in ('local', 'ignore') for v in decided)} matched, "
          f"{sum(':local-' in key for key in games)} local games, {sum(v == 'ignore' for v in decided)} ignored; "
          f"MobyGames: {sum(v != 'own' for v in moby_decisions['games'].values())} linked, "
          f"{sum(v == 'own' for v in moby_decisions['games'].values())} own; "
          f"{len(shared)} shared media left out; {sum('description' in g for g in games.values())} described")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("root", type=Path)
    parser.add_argument("--mobygames", type=Path, required=True)
    args = parser.parse_args()
    assemble(args.root.resolve(), args.mobygames)


if __name__ == "__main__":
    main()
