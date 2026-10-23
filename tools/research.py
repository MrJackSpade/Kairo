#!/usr/bin/env python3
"""Assemble an app's catalog research from LaunchBox, MobyGames, media scans and decisions.

Usage: research.py <app root> --mobygames <dump directory>

Writes catalog/research/games-v1.json for tools/app_catalog.py. The app's tools/catalog_config.py
names its PLATFORMS (LaunchBox slugs), MOBYGAMES (its MobyGames listing slug) and SCANS:
{set name: (scan index path, LaunchBox slugs to match against, in order)}. An optional
GROUP(archive path) names the game an archive belongs to (default: its file name, or for a disk
of a set its set's name, disk_sets.group); archives of one group share a title hint and, when
unmatched, one local game. An optional NOT_GAME(archive path)
marks archives that are not games, such as utilities, system disks and firmware: a game whose
archives are all not games is hidden from the library, so it never shows even in a games folder.

Decisions live beside the output and are kept across runs; new items are decided automatically
and the decision is recorded so it can be reviewed and corrected:
  media-matches-v1.json  {"<set>:<archive path>": "<game key>" | "local" | "ignore"}; an archive
                         nothing matches is local without a recorded decision
  moby-matches-v1.json   {"<MobyGames ID>": "<game key>" | "own"}
  descriptions-v1.json   {"<game key>": "original description"}
  overrides-v1.json      {"<game key>": {"title"|"aliases"|"adult"|"artwork"|"controller": ...}} applied last
A game LaunchBox has no cover for takes its MobyGames cover and screenshot (moby_art.py), encoded
into catalog/artwork; moby-artwork-v1.json records their sources.
Images the manual artwork review rejected (art-review-v1.json, art_verdicts.py) are never picked;
the game takes its next candidate image instead.
Every adult game is added to catalog/core-review-v1.json's exclusions if it is not there yet. A
medium found in several games' sets identifies none of them: it is listed under sharedContentIds.
A game's disks form sets (disk_sets.py, by the app's BOOT_KINDS and COMPANION): "diskSets" maps each
set's entry disk to the disks hidden behind it and the disk it boots with; "diskHidden" lists the
disks hidden on their own, behind a set entry shared with other games. An optional
MACHINE(disk name, scan set name, content ID) gives the machine settings a disk needs: "diskMachine"
maps the disk to them.

An archive matches a LaunchBox game when its file name's title equals the game's name or an
alias and the years agree within one, or when the closest fuzzy title scores at least 0.93 with
agreeing years, the same sequel numbers and no tie. Failing that, it joins a MobyGames-only game of exactly its title. Anything else
becomes a "local" game titled from its group.
"""

import argparse
import hashlib
import importlib.util
import json
import re
from pathlib import Path

from launchbox import BOX_TYPES, SCREENSHOT_TYPES, candidates, key as title_key
from moby_art import MobyArt
from disk_sets import disk_sets, group as disk_group, ungrouped_sets
import art_verdicts
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
    # TOSEC moves a leading article to the end: "New Zealand Story, The", or "Horde,The".
    article = re.match(r"^(.*?),\s*(The|A|An)$", title)
    if article:
        title = f"{article[2]} {article[1]}"
    groups = [group.strip() for group in re.findall(r"\(([^)]*)\)", stem)]
    year = next((int(group[:4]) for group in groups if YEAR.match(group)), None)
    publisher = next((group for group in groups if not re.match(r"(?:19|20)[0-9x]{2}", group)), None)
    return title, year, publisher


def year_of(game):
    date = game.get("ReleaseDate") or ""
    return int(date[:4]) if date[:4].isdigit() else None


def years_agree(a, b):
    """Sources often date a game a year apart (original release, port, region)."""
    return a is None or b is None or abs(a - b) <= 1


def first_url(game, categories, rejected):
    """The first LaunchBox image of the earliest category whose URL compacts to an artwork
    reference and that the artwork review has not rejected."""
    for category in categories:
        for image in game["images"]:
            if image["type"] == category and URL.fullmatch(image["url"]) and image["url"] not in rejected:
                return image["url"]
    return ""


def launchbox_entry(slug, game, rejected):
    entry = {"title": game["Name"], "launchbox": game["DatabaseID"]}
    if game["aliases"]:
        entry["aliases"] = [alias for alias in game["aliases"] if alias != game["Name"]]
    for field, source in (("developer", "Developer"), ("publisher", "Publisher")):
        if game.get(source):
            entry[field] = game[source]
    if year_of(game):
        entry["year"] = year_of(game)
    for field, categories in (("boxArtUrl", BOX_TYPES), ("screenshotUrl", SCREENSHOT_TYPES)):
        url = first_url(game, categories, rejected)
        if url:
            entry[field] = url
    return entry


ROMAN = {"II": "2", "III": "3", "IV": "4", "V": "5", "VI": "6", "VII": "7", "VIII": "8", "IX": "9", "X": "10"}


def sequel_numbers(title):
    """A title's standalone numbers, Roman numerals read as numbers and a lone 1 as none, so
    "Hydlide 2" does not match "Hydlide" and "Sangokusi 2" still matches "Sangokushi II"."""
    tokens = re.findall(r"[0-9]+|\b[IVX]{1,4}\b", title)
    return {ROMAN.get(token, token.lstrip("0") or "0") for token in tokens} - {"1"}


def name_forms(name):
    """A name's comparison forms: as written, "&" read as "and", and without a subtitle after
    ":" or " - ", so "Sim Earth" finds "SimEarth: The Living Planet"."""
    forms = {name, name.replace("&", " and ")}
    for separator in (":", " - "):
        if separator in name:
            forms.add(name.split(separator, 1)[0])
    return {title_key(form) for form in forms if title_key(form)}


LONG_VOWELS = {"ō": ("ou", "o"), "ô": ("ou", "o"), "ū": ("uu", "u"), "ā": ("aa", "a"),
               "ē": ("ee", "e"), "ī": ("ii", "i")}


def romanizations(title):
    """A title's comparison forms with Hepburn long vowels written out or plain: MobyGames'
    "Kyōdan" is LaunchBox's "Kyoudan" or "Kyodan"."""
    forms = [title.replace("&", " and ")]
    for vowel, spellings in LONG_VOWELS.items():
        if any(vowel in form.lower() for form in forms):
            forms = [form.replace(vowel, spelling).replace(vowel.upper(), spelling.capitalize())
                     for form in forms for spelling in spellings]
    return {title_key(form) for form in forms}


def match_title(title, year, slugs, snapshots):
    """The one LaunchBox game key a title names, searching platforms in order, or None."""
    sought = romanizations(title)
    for slug in slugs:
        games = snapshots[slug]
        exact = [game_id for game_id, game in games.items()
                 if any(sought & name_forms(name) for name in (game["Name"], *game["aliases"]))
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
    rejected = art_verdicts.rejected(root)
    games = {f"{slug}:{game_id}": launchbox_entry(slug, game, rejected)
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
    group_of = getattr(config, "GROUP", disk_group)
    not_game = getattr(config, "NOT_GAME", lambda path: False)
    machine_of = getattr(config, "MACHINE", lambda name, set_name, content_id: {})
    archive_counts = {}  # each game's (archives, archives that are not games)
    disks = {}  # each game's disks: archive, member and content ID (disk_sets.py)
    scanned = []  # every ready archive of the standard sets, for the grouping check
    for set_name, (index_path, slugs) in config.SCANS.items():
        index = read(root / index_path, None)
        if index is None:
            raise SystemExit(f"missing media scan {index_path}")
        for archive in index["archives"]:
            if archive["status"] != "ready":
                continue
            scanned.append(archive)
            name = f"{set_name}:{archive['path']}"
            group = group_of(archive["path"])
            title, year, _ = parse_hint(group)
            decision = media_decisions["archives"].get(name)
            if decision is None:
                decision = match_title(title, year, slugs, snapshots) or own_moby.get(title_key(title))
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
            total, other = archive_counts.get(key, (0, 0))
            archive_counts[key] = (total + 1, other + bool(not_game(archive["path"])))
            disks.setdefault(key, []).extend({"archive": archive["path"], "member": disk.get("entry"),
                                              "contentId": disk["contentId"]} for disk in archive["disks"])
            for disk in archive["disks"]:
                settings = machine_of(disk.get("entry") or archive["path"].rsplit("/", 1)[-1], set_name,
                                      disk["contentId"])
                if settings:
                    entry.setdefault("diskMachine", {})[disk["contentId"]] = settings
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

    for key, (total, other) in archive_counts.items():
        if other == total:
            games[key]["hidden"] = True
    for entry in games.values():
        if "diskMachine" in entry:
            entry["diskMachine"] = {disk: settings for disk, settings in sorted(entry["diskMachine"].items())
                                    if disk not in shared}
            if not entry["diskMachine"]:
                del entry["diskMachine"]

    # One library entry per disk set: the entry disk names the others, hidden behind it, and the
    # disk it boots with (disk_sets.py).
    # A disk shared with other games' sets (a series' common boot or sound disk) is in this game's
    # sets too, so it hides like any other disk. It cannot carry this game's record, though: when it
    # starts a set, the set's other disks are hidden by records of their own ("diskHidden").
    # A shared disk that starts a set in one game stays listed: no other game's set hides it.
    all_sets = {key: disk_sets(game_disks, config.BOOT_KINDS, config.COMPANION) for key, game_disks in sorted(disks.items())}
    shared_entries = {entry for sets in all_sets.values() for entry in sets if entry in shared}
    for key, sets in all_sets.items():
        hidden = sorted({disk for entry, fields in sets.items() if entry in shared
                         for disk in fields["setDisks"] if disk not in shared} - sets.keys())
        sets = {entry: {**fields, "setDisks": [disk for disk in fields["setDisks"] if disk not in shared_entries]}
                for entry, fields in sets.items() if entry not in shared}
        sets = {entry: fields for entry, fields in sets.items() if fields["setDisks"] or "media" in fields}
        if sets:
            games[key]["diskSets"] = sets
        if hidden:
            games[key]["diskHidden"] = hidden

    # Every multi-disk set of the standard sets is one library entry; a set that is not fails here.
    ungrouped = ungrouped_sets(scanned, games)
    if ungrouped:
        raise SystemExit(f"{len(ungrouped)} multi-disk sets are more than one library entry: " +
                         "; ".join(f"{name} ({len(shown)} entries)" for name, shown in ungrouped[:10]))

    descriptions = read(research / "descriptions-v1.json", {"schemaVersion": 1, "descriptions": {}})["descriptions"]
    unknown = descriptions.keys() - games.keys()
    if unknown:
        raise SystemExit(f"descriptions for unknown games: {sorted(unknown)[:5]}")
    for key, text in descriptions.items():
        games[key]["description"] = text

    overrides = read(research / "overrides-v1.json", {"schemaVersion": 1, "games": {}})["games"]
    for key, fields in overrides.items():
        if key not in games or fields.keys() - {"title", "aliases", "adult", "artwork", "controller"}:
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

    # A reviewed artwork pick loses an image the artwork review rejected; a pick left with no
    # image is dropped, so the game's other sources are tried.
    for entry in games.values():
        if "artwork" not in entry:
            continue
        art = config.ARTWORK.expand(entry["artwork"])
        dropped = [kind for kind in ("boxArt", "preview") if art.get(kind + "Url") in rejected]
        for kind in dropped:
            del art[kind], art[kind + "Url"]
        if not any(kind in art for kind in ("boxArt", "preview")):
            del entry["artwork"]
        elif dropped:
            entry["artwork"] = config.ARTWORK.compact(art)

    # MobyGames artwork for a game LaunchBox has no cover for and no reviewed
    # artwork pick names (moby_art.py).
    moby_art = MobyArt(root, moby_root, config.MOBYGAMES, config.ARTWORK, rejected)
    uncovered = {key: entry["mobygames"] for key, entry in sorted(games.items())
                 if "mobygames" in entry and not entry.get("boxArtUrl") and "artwork" not in entry}
    moby_art.require(uncovered.values())
    for key, game_id in uncovered.items():
        artwork = moby_art.artwork(key, game_id, cover_required=bool(games[key].get("screenshotUrl")))
        if artwork:
            games[key].pop("screenshotUrl", None)
            games[key]["artwork"] = artwork

    # Adult games leave the core review; existing exclusions and their reasons are kept.
    review_path = root / "catalog/core-review-v1.json"
    review = read(review_path, {"schemaVersion": 1, "excluded": {}, "adultArtwork": {}})
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
    write(research / "moby-artwork-v1.json", moby_art.provenance())
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
