#!/usr/bin/env python3
"""Generate an app's catalog from its research file.

Usage: app_catalog.py <app root> [--check]

The app's tools/catalog_config.py names its APP (a catalog_parts.APPS key), its media KINDS, its
ARTWORK references and its MEDIA_EXTENSIONS, the file extensions the app removes from a file's name
before looking it up (Schema.mediaExtensions, the same pattern). catalog/research/games-v1.json holds one record per game:

  {"schemaVersion": 1, "games": {"<platform>:<id>": {
      "title": "...", "aliases": [...], "description": "...",   shipped display metadata
      "tags": ["1993", "Action", ...],                          shipped tags (year, genre, players)
      "adultDescription": "...",                                an adult game's frank description, shipped
                                                                only in the adult data part (data.nsfw)
      "descriptions": {"<language>": "..."},                    the description translated (catalog_languages)
      "titles": {"<language>": "..."},                          the game's own title in a language, such as the
                                                                Japanese one it was released under, shown on a
                                                                device in that language (Kairo#61)
      "adultDescriptions": {"<language>": "..."},               the frank description translated, in data.nsfw
      "boxArtUrl": "...", "screenshotUrl": "...",               LaunchBox images, compacted
      "artwork": {...},                                         or a reviewed compact pick
      "contentIds": [...], "sourceGroups": [...],               media this game is known by
      "diskSets": {"<entry>": {"setDisks": [...], "media": [...]}},   one library entry per disk set
      "diskMachine": {"<disk>": {"<option>": "<value>"}},       the machine a disk asks for, its "commands" (the
                                                                lines typed at the machine's prompt to start it) and
                                                                "launch" (other launch fields, LAUNCH_FIELDS)
      "diskAutomation": {"<disk>": {"startupChoices": [...], "diskSwaps": [...]}},
                                                                the disk's startup menus and disk swaps
                                                                (startup_automation.py; swaps need DISK_SWAPS)
      "diskHidden": [...],                                      disks behind a set entry other games share
      "diskCollection": {"<disk>": {"<member>": {...}}},       one medium holding several games, a collection: the
                                                                player picks a member ("1", "2"...) when it
                                                                starts, each with its game and launch (its
                                                                record's "collection")
      "adult": true,                                            heart marker, core-review exclusion
      "hidden": true,                                           not a game: never listed in the library
      "controller": {"profile": "...", "defaults": {"withoutSticks": [...], "withSticks": [...]}}
                                                                the game's own controls and the preset they are
      "machine": {...}, "launch": {...}, "input": {...}         the game's own machine settings (MACHINE_OPTIONS),
                                                                launch fields and touch input, for all its disks
      "named": false,                                           never recognized by file name (a game known only
                                                                by its disks): left out of the name index
      "lookupNames": [...],                                     more names it is recognized by, not shipped
      "launchbox": "...", "mobygames": 0, "year": 0, ...         research only, never shipped
  }}}

catalog/system-v1.json is the system's definition: its controller mapping,
{"schemaVersion": 1, "controller": {"bindings": [...]}}, the lowest layer of every game's mapping
(shared ControllerDefaults.cascade). It binds every control (the D-pad to the arrow keys) and holds
no explicit null ("unbound"): what we ship leaves no control dead.

An app's catalog_config.DOCUMENTS(games), when it has one, returns its own documents
{"<name>.json": {...}}, shipped beside the records and carried in the update's "documents"
(shared LayeredGameCatalog.documents), such as KairoDos's launch folders.

catalog/core-review-v1.json lists excluded games and approved artwork. Outputs: catalog/source-v1.json,
catalog/core-v4.zip with its .meta.json (the update: the catalog's documents, shared
catalog_documents.update_archive), catalog/parts/, the artwork exclusion list and the
importable artwork package. --check regenerates in memory and fails if a committed file differs.
"""

import argparse
import hashlib
import importlib.util
import json
import re
import sys
import unicodedata
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import catalog_documents
import catalog_tags
import startup_automation
from catalog_documents import compact, require
from catalog_languages import LANGUAGES, valid_titles, valid_translations
from catalog_package import filter_artwork, image_paths
from catalog_parts import artwork_split, generate_parts

GAME_KEY = re.compile(r"[a-z0-9]+:[A-Za-z0-9._-]{1,64}\Z")
# A collection member's key: "1", "2"... in the order the player sees them.
COLLECTION_MEMBER = re.compile(r"[1-9][0-9]?\Z")
SHIPPED = ("title", "description", "aliases", "tags", "controller", "machine", "launch", "input")
# A game's touch input, as the apps read it (LayeredGameCatalog).
INPUT = {"mode": {"auto", "keyboard", "mouse"}, "touch": {"touchpad", "direct"}, "secondary": {"keyboard", "touchpad"}}
MAX_IDS = 64


def shipped_tag_set(documents):
    """Every tag in [documents]' records, collection members included."""
    tags, stack = set(), list(documents)
    while stack:
        node = stack.pop()
        if isinstance(node, dict):
            if isinstance(node.get("tags"), list):
                tags.update(node["tags"])
            stack.extend(node.values())
        elif isinstance(node, list):
            stack.extend(node)
    return tags


def load_config(root):
    spec = importlib.util.spec_from_file_location("catalog_config", root / "tools/catalog_config.py")
    config = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(config)
    return config


# The disk numbers the apps take out of a file's name (LayeredGameCatalog.DISK_NUMBER).
DISK_NUMBER = re.compile(r"\((?:disk|disc|fd)\s*\d+[^)]*\)", re.I)


def lookup_name(value, media_extensions):
    """A name's index form, as the app looks a file's name up (LayeredGameCatalog.normalizedName):
    bracketed annotations, disk numbers and the app's own [media_extensions] removed, then NFKC,
    lower-case, letters and digits only."""
    value = re.sub(r"\[[^]]*]", "", value)
    value = DISK_NUMBER.sub("", value)
    value = media_extensions.sub("", value)
    return "".join(char.lower() for char in unicodedata.normalize("NFKC", value) if char.isalnum())


COMMAND_LINE = re.compile(r"[ -~]{1,40}")


def validator(kinds, artwork, media_roles=frozenset(), machine_options=None, launch_fields=None, disk_swaps=None):
    """[launch_fields] maps each launch field an app has besides "commands" to the pattern its value
    matches, or to a function that says whether a value is valid. [disk_swaps] is the app's
    DISK_SWAPS, the media slots its disk swaps use and the kinds of disk each takes."""
    launch_fields = {name: rule if callable(rule) else
                     (lambda value, pattern=re.compile(rule): isinstance(value, str) and bool(pattern.fullmatch(value)))
                     for name, rule in (launch_fields or {}).items()}
    content_id = re.compile(r"sha256-(?:" + "|".join(sorted(kinds)) + r")-v1:[0-9a-f]{64}\Z")

    def validate_record(record):
        require(isinstance(record, dict), "game must be an object")
        require(set(record) <= {"game", "title", "description", "descriptions", "aliases", "tags", "artwork", "heart",
                                "hidden", "setDisks", "media", "machine", "launch", "controller", "collection",
                                "contentIds", "startupChoices", "diskSwaps", "input"},
                "unknown game field")
        collection = record.get("collection", {})
        require(isinstance(collection, dict) and len(collection) != 1 and len(collection) <= 16 and
                all(isinstance(member, str) and COLLECTION_MEMBER.fullmatch(member) and isinstance(v, dict)
                    and "game" in v and "collection" not in v for member, v in collection.items()), "invalid collection")
        ids = record.get("contentIds")
        require(isinstance(ids, list) and 1 <= len(ids) <= MAX_IDS and len(ids) == len(set(ids)) and
                all(isinstance(value, str) and content_id.fullmatch(value) for value in ids),
                "contentIds must list 1-64 distinct content IDs")
        for member in collection.values():
            validate_record({**member, "contentIds": ids})
        require("game" not in record or (isinstance(record["game"], str) and GAME_KEY.fullmatch(record["game"])),
                "invalid game reference")
        title = record.get("title")
        require((title is None and "game" in record) or
                (isinstance(title, str) and 0 < len(title.strip()) <= 256), "invalid title")
        description = record.get("description")
        require(description is None or (isinstance(description, str) and 0 < len(description.strip()) <= 8000),
                "invalid description")
        require("descriptions" not in record or (description is not None and valid_translations(record["descriptions"])),
                "invalid translated descriptions")
        tags = record.get("tags", [])
        require(isinstance(tags, list) and len(tags) <= 32 and
                all(isinstance(x, str) and 0 < len(x.strip()) <= 128 for x in tags), "invalid tags")
        aliases = record.get("aliases", [])
        require(isinstance(aliases, list) and len(aliases) <= 64 and
                all(isinstance(x, str) and 0 < len(x.strip()) <= 256 for x in aliases), "invalid aliases")
        require("heart" not in record or type(record["heart"]) is bool, "invalid heart marker")
        require("hidden" not in record or type(record["hidden"]) is bool, "invalid hidden flag")
        set_disks = record.get("setDisks", [])
        require(isinstance(set_disks, list) and len(set_disks) <= 128 and
                all(isinstance(x, str) and content_id.fullmatch(x) for x in set_disks), "invalid setDisks")
        media = record.get("media", [])
        require(isinstance(media, list) and len(media) <= 4 and
                all(isinstance(m, dict) and set(m) == {"role", "contentId"} and m["role"] in media_roles and
                    isinstance(m["contentId"], str) and content_id.fullmatch(m["contentId"]) for m in media),
                "invalid media")
        if "controller" in record:
            controller = record["controller"]
            defaults = controller.get("defaults") if isinstance(controller, dict) else None
            profile = controller.get("profile") if isinstance(controller, dict) else None
            require(profile is None or (isinstance(profile, str) and 0 < len(profile) <= 128), "invalid controller profile")
            require(isinstance(controller, dict) and set(controller) - {"profile"} == {"defaults"} and isinstance(defaults, dict) and
                    "withoutSticks" in defaults and set(defaults) <= {"withoutSticks", "withSticks"} and
                    all(isinstance(bindings, list) and all(isinstance(b, dict) and isinstance(b.get("input"), str)
                                                           for b in bindings) for bindings in defaults.values()),
                    "invalid controller defaults")
            # A game's layer replaces what it binds; it never ships an explicit null.
            require(not any("unbound" in b or set(b) == {"input"} for bindings in defaults.values() for b in bindings),
                    "controller defaults ship an explicit null")
        if "launch" in record:
            # The lines typed at the machine's prompt to start the game, one command each.
            # The lines typed at the machine's prompt to start the game, one command each, and the
            # app's other launch fields (LAUNCH_FIELDS), such as the program a desktop starts.
            launch = record["launch"]
            require(isinstance(launch, dict) and launch and set(launch) <= {"commands", *launch_fields},
                    "unknown launch field")
            if "commands" in launch:
                commands = launch["commands"]
                require(isinstance(commands, list) and 1 <= len(commands) <= 8 and
                        all(isinstance(line, str) and COMMAND_LINE.fullmatch(line) for line in commands),
                        "invalid startup commands")
            for field, valid in launch_fields.items():
                require(field not in launch or valid(launch[field]), f"invalid launch {field}")
        if "input" in record:
            touch = record["input"]
            require(isinstance(touch, dict) and touch and all(value in INPUT.get(name, ()) for name, value in touch.items()),
                    "invalid input")
        if "startupChoices" in record:
            startup_automation.validate_choices(record["startupChoices"])
        if "diskSwaps" in record:
            require(disk_swaps is not None, "disk swaps need the app's DISK_SWAPS")
            startup_automation.validate_swaps(record["diskSwaps"], disk_swaps, content_id)
        machine = record.get("machine", {})
        require(isinstance(machine, dict) and ("machine" not in record or machine_options) and
                all(value in machine_options.get(option, ()) for option, value in machine.items()),
                "invalid machine settings")
        artwork.expand(record.get("artwork", {}))
        return {key: value for key, value in record.items() if key != "contentIds"}

    return validate_record


def validate_system(system):
    """The system's definition: one controller mapping, every binding an input and what it sends."""
    controller = system.get("controller") if isinstance(system, dict) else None
    bindings = controller.get("bindings") if isinstance(controller, dict) else None
    require(system.get("schemaVersion") == 1 and set(system) == {"schemaVersion", "controller"} and
            set(controller) == {"bindings"} and isinstance(bindings, list) and bindings,
            "invalid system definition")
    inputs = [b.get("input") for b in bindings if isinstance(b, dict)]
    require(len(inputs) == len(bindings) and all(isinstance(i, str) and i.startswith("virtual:") for i in inputs) and
            len(set(inputs)) == len(inputs), "invalid system controller bindings")
    require(not any("unbound" in b or set(b) == {"input"} for b in bindings),
            "the system definition ships an explicit null")


def artwork_group(entry):
    """A game's artwork group, which names its image cache folder: its LaunchBox DatabaseID.
    (Kairo98 numbered groups by public page ID, which has no reliable mapping for older
    DatabaseIDs; reviewed picks carried over from it keep their own group in "artwork".)"""
    return int(entry["launchbox"])


def metadata(key, entry, config):
    record = {field: entry[field] for field in SHIPPED if entry.get(field)}
    if entry.get("adult"):
        record["heart"] = True
    if entry.get("hidden"):
        record["hidden"] = True
    urls = {kind: entry[field] for field, kind in (("boxArtUrl", "boxArt"), ("screenshotUrl", "preview"))
            if entry.get(field)}
    if "artwork" in entry:
        # A reviewed pick, kept exactly as reviewed.
        require(not urls, f"{key}: artwork and image URLs both given")
        config.ARTWORK.expand(entry["artwork"])
        record["artwork"] = entry["artwork"]
    elif urls:
        require("launchbox" in entry, f"{key}: LaunchBox images need the game's LaunchBox DatabaseID")
        record["artwork"] = config.ARTWORK.from_urls(config.ARTWORK_CODE, artwork_group(entry), urls)
    return record


def meta(name, content):
    """The metadata an app checks a download against: its archive's name, SHA-256 and size."""
    return (json.dumps({"schemaVersion": 1, "archive": name, "sha256": hashlib.sha256(content).hexdigest(),
                        "size": len(content)}, separators=(",", ":")) + "\n").encode("utf-8")


def generate(root, check=False):
    config = load_config(root)
    # The media roles a game's disks fill: its companions', or MEDIA_ROLES for an app without disk sets.
    media_roles = getattr(config, "MEDIA_ROLES", {rule[1] for rule in config.COMPANION.values()})
    validate_record = validator(config.KINDS, config.ARTWORK, media_roles,
                                getattr(config, "MACHINE_OPTIONS", None), getattr(config, "LAUNCH_FIELDS", None),
                                getattr(config, "DISK_SWAPS", None))
    media_extensions = re.compile(config.MEDIA_EXTENSIONS, re.I)
    research = json.loads((root / "catalog/research/games-v1.json").read_text(encoding="utf-8"))
    require(research.get("schemaVersion") == 1, "unsupported research schema")
    policy = json.loads((root / "catalog/core-review-v1.json").read_text(encoding="utf-8"))
    games, names, source_games = {}, {}, []
    for key, entry in sorted(research["games"].items()):
        require(GAME_KEY.fullmatch(key) and key.split(":", 1)[0] in config.PLATFORMS, f"invalid game key {key}")
        require(not entry.get("adult") or key in policy["excluded"], f"adult game missing from core review: {key}")
        record = metadata(key, entry, config)
        validate_record({**record, "contentIds": [f"sha256-{sorted(config.KINDS)[0]}-v1:" + "0" * 64]})
        games[key] = record
        # A disk with fields of its own: a disk set's entry names the disks hidden behind it and the
        # disk it boots with, and a disk may ask for machine settings.
        own = {disk: dict(fields) for disk, fields in entry.get("diskSets", {}).items()}
        for disk in entry.get("diskHidden", []):
            own.setdefault(disk, {})["hidden"] = True
        for disk, automation in entry.get("diskAutomation", {}).items():
            require(isinstance(automation, dict) and automation and set(automation) <= {"startupChoices", "diskSwaps"},
                    f"{key}: invalid diskAutomation for {disk}")
            own.setdefault(disk, {}).update(automation)
        for disk, members in entry.get("diskCollection", {}).items():
            own.setdefault(disk, {})["collection"] = members
        for disk, settings in entry.get("diskMachine", {}).items():
            settings = dict(settings)
            # The startup commands and other launch fields are the disk's launch; the rest are machine settings.
            launch = dict(settings.pop("launch", {}))
            commands = settings.pop("commands", None)
            if commands:
                launch["commands"] = commands
            if settings:
                own.setdefault(disk, {})["machine"] = settings
            if launch:
                own.setdefault(disk, {})["launch"] = launch
        for disk, fields in sorted(own.items()):
            require(disk in entry.get("contentIds", []), f"{key}: {disk} is not one of its disks")
            source_games.append({"contentIds": [disk], "game": key, **fields})
        ids = sorted(set(entry.get("contentIds", [])) - own.keys())
        for start in range(0, len(ids), MAX_IDS):
            source_games.append({"contentIds": ids[start:start + MAX_IDS], "game": key})
        if entry.get("named", True) is False:
            continue
        for candidate in (entry["title"], *entry.get("aliases", []), *entry.get("lookupNames", []),
                          *(group.rsplit(":", 1)[-1] for group in entry.get("sourceGroups", []))):
            normalized = lookup_name(candidate, media_extensions)
            if 4 <= len(normalized) <= 128:
                names.setdefault(normalized, set()).add(key)
    require(policy["excluded"].keys() <= games.keys(), "core review excludes unknown games")
    unique_names = {name: next(iter(keys)) for name, keys in sorted(names.items()) if len(keys) == 1}
    name_index = {"schemaVersion": 1, "games": games, "names": unique_names}
    # An app names where its research comes from when it is not LaunchBox and MobyGames (KairoDos: eXoDOS).
    provenance = getattr(config, "PROVENANCE", {
        "source": f"{config.APP} research catalog: LaunchBox Games Database and MobyGames references, local media scans",
        "license": "Original descriptions; titles and image references from LaunchBox; artwork used under fair use",
        "attribution": "LaunchBox Games Database contributors; MobyGames contributors; original descriptions"})
    source = {"schemaVersion": 1, "datasets": [{"id": f"{config.APP}-research", "provenance": provenance,
                                                "games": source_games}]}
    manifest, shards = catalog_documents.build(source, validate_record)
    system = json.loads((root / "catalog/system-v1.json").read_text(encoding="utf-8"))
    validate_system(system)
    own_documents = config.DOCUMENTS(research["games"]) if hasattr(config, "DOCUMENTS") else {}
    require(not own_documents.keys() & {"base-v1.json", "manifest-v1.json", "name-index-v1.json", "system-v1.json"}
            and all(not name.startswith("shards/") for name in own_documents), "an app document takes a catalog name")
    # Each language's descriptions in a document of its own, beside the English records.
    translations = catalog_documents.translation_documents(
        {key: entry["descriptions"] for key, entry in research["games"].items() if entry.get("descriptions")})
    # With them, each language's words for the tags (genre, players) the shipped records carry (KairoDos#141).
    shipped_tags = shipped_tag_set([own_documents, name_index, *shards.values()])
    for language, names in catalog_tags.translated_tags(shipped_tags).items():
        if names:
            translations.setdefault(f"descriptions/{language}.json", {"games": {}})["tags"] = names
    # And each game's own title in a language, such as its Japanese title (Kairo#61).
    for key, entry in sorted(research["games"].items()):
        if "titles" not in entry:
            continue
        require(valid_titles(entry["titles"]), f"{key}: invalid titles")
        for language, title in sorted(entry["titles"].items()):
            translations.setdefault(f"descriptions/{language}.json", {"games": {}}).setdefault("titles", {})[key] = title
    documents = {**own_documents, **translations, "base-v1.json": {"schemaVersion": 1, "games": {}},
                 "manifest-v1.json": manifest, "name-index-v1.json": name_index, "system-v1.json": system,
                 **{f"shards/{prefix}.json": {"schemaVersion": 1, "games": records}
                    for prefix, records in shards.items()}}
    placeholder = f"sha256-{sorted(config.KINDS)[0]}-v1:" + "0" * 64
    # What is published names only the main artwork, as every build ships it: the adult images reach a
    # build through its art.nsfw part or the importable package alone (catalog_parts).
    approved, _ = artwork_split(policy, image_paths(documents, config.ARTWORK.expand))
    def main_artwork(value):
        return filter_artwork(value, approved, config.ARTWORK.expand, config.ARTWORK.compact)
    published_source = main_artwork(source)
    catalog_documents.build_pack(published_source, main_artwork(name_index), validate_record, placeholder)
    # The update in English, and each language's descriptions as a download of their own: an app
    # fetches its device language's beside the English one.
    update = catalog_documents.update_archive({name: main_artwork(document) for name, document in documents.items()
                                               if name not in translations})
    outputs = {"catalog/source-v1.json": compact(published_source), "catalog/core-v4.zip": update,
               "catalog/core-v4.meta.json": meta("core-v4.zip", update)}
    for name, document in translations.items():
        language = name.removeprefix("descriptions/").removesuffix(".json")
        archive = catalog_documents.update_archive({name: document})
        outputs.update({f"catalog/core-v4-{language}.zip": archive,
                        f"catalog/core-v4-{language}.meta.json": meta(f"core-v4-{language}.zip", archive)})
    # App versions before the v4 update read catalog/core-v3.json, which stays published as it was
    # last built: they receive no further catalog fixes (Kairo#77).
    for path, content in outputs.items():
        if check:
            require((root / path).read_bytes() == content, f"Stale {path}")
        else:
            (root / path).parent.mkdir(parents=True, exist_ok=True)
            (root / path).write_bytes(content)
    optional = {"schemaVersion": 1, "games": {}, "nameIndex": name_index}
    # Kept out of the name index and the update: they reach a build only through data.nsfw.
    adult = {"name-index-v1.json": {"games": {key: {"description": entry["adultDescription"]}
             for key, entry in sorted(research["games"].items()) if entry.get("adultDescription")}},
             **catalog_documents.translation_documents(
                 {key: entry["adultDescriptions"] for key, entry in research["games"].items()
                  if entry.get("adultDescriptions")})}
    generate_parts(root, config.APP, documents, optional, adult, config.ARTWORK.expand, config.ARTWORK.compact, check)
    print(f"{config.APP}: {len(games)} games, {manifest['games']} content IDs, {len(unique_names)} unique names, "
          f"{len(image_paths(name_index, config.ARTWORK.expand))} artwork paths")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("root", type=Path)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    generate(args.root.resolve(), args.check)


if __name__ == "__main__":
    main()
