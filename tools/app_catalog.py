#!/usr/bin/env python3
"""Generate an app's catalog from its research file.

Usage: app_catalog.py <app root> [--check]

The app's tools/catalog_config.py names its APP (a catalog_parts.APPS key), its media KINDS and
its ARTWORK references. catalog/research/games-v1.json holds one record per game:

  {"schemaVersion": 1, "games": {"<platform>:<id>": {
      "title": "...", "aliases": [...], "description": "...",   shipped display metadata
      "tags": ["1993", "Action", ...],                          shipped tags (year, genre, players)
      "adultDescription": "...",                                an adult game's frank description, shipped
                                                                only in the adult data part (data.nsfw)
      "descriptions": {"<language>": "..."},                    the description translated (catalog_languages)
      "adultDescriptions": {"<language>": "..."},               the frank description translated, in data.nsfw
      "boxArtUrl": "...", "screenshotUrl": "...",               LaunchBox images, compacted
      "artwork": {...},                                         or a reviewed compact pick
      "contentIds": [...], "sourceGroups": [...],               media this game is known by
      "diskSets": {"<entry>": {"setDisks": [...], "media": [...]}},   one library entry per disk set
      "diskMachine": {"<disk>": {"<option>": "<value>"}},       the machine a disk asks for, its "commands" (the
                                                                lines typed at the machine's prompt to start it) and
                                                                "launch" (other launch fields, LAUNCH_FIELDS)
      "diskHidden": [...],                                      disks behind a set entry other games share
      "diskVariants": {"<disk>": {"<file name>": {...}}},       one medium holding several games: the game and
                                                                launch a library file's name picks (its
                                                                record's "variants", Schema.variantName)
      "adult": true,                                            heart marker, core-review exclusion
      "hidden": true,                                           not a game: never listed in the library
      "controller": {"profile": "...", "defaults": {"withoutSticks": [...], "withSticks": [...]}}
                                                                the game's own controls and the preset they are
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
catalog/core-v3.json with its .meta.json, catalog/parts/, the artwork exclusion list and the
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
from catalog_documents import compact, require
from catalog_languages import LANGUAGES, valid_translations
from catalog_package import image_paths
from catalog_parts import generate_parts

GAME_KEY = re.compile(r"[a-z0-9]+:[A-Za-z0-9._-]{1,64}\Z")
SHIPPED = ("title", "description", "descriptions", "aliases", "tags", "controller")
CORE_VERSION = 3
MAX_IDS = 64


def load_config(root):
    spec = importlib.util.spec_from_file_location("catalog_config", root / "tools/catalog_config.py")
    config = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(config)
    return config


def lookup_name(value):
    """A name's index form: annotations, disk labels and a file extension removed, then NFKC,
    lower-case, letters and digits only."""
    value = re.sub(r"\[[^]]*]", "", value)
    value = re.sub(r"\((?:disk|disc|fd|side)\s*[0-9a-z]{1,3}[^)]*\)", "", value, flags=re.I)
    value = re.sub(r"\.[A-Za-z0-9]{2,4}$", "", value)
    return "".join(char.lower() for char in unicodedata.normalize("NFKC", value) if char.isalnum())


COMMAND_LINE = re.compile(r"[ -~]{1,40}")


def validator(kinds, artwork, media_roles=frozenset(), machine_options=None, launch_fields=None):
    """[launch_fields] maps each launch field an app has besides "commands" to the pattern its value
    matches, or to a function that says whether a value is valid."""
    launch_fields = {name: rule if callable(rule) else
                     (lambda value, pattern=re.compile(rule): isinstance(value, str) and bool(pattern.fullmatch(value)))
                     for name, rule in (launch_fields or {}).items()}
    content_id = re.compile(r"sha256-(?:" + "|".join(sorted(kinds)) + r")-v1:[0-9a-f]{64}\Z")

    def validate_record(record):
        require(isinstance(record, dict), "game must be an object")
        require(set(record) <= {"game", "title", "description", "descriptions", "aliases", "tags", "artwork", "heart",
                                "hidden", "setDisks", "media", "machine", "launch", "controller", "variants",
                                "contentIds"},
                "unknown game field")
        variants = record.get("variants", {})
        require(isinstance(variants, dict) and len(variants) <= 16 and
                all(isinstance(name, str) and 0 < len(name) <= 256 and name == name.lower() and isinstance(v, dict)
                    and v and "variants" not in v for name, v in variants.items()), "invalid variants")
        ids = record.get("contentIds")
        require(isinstance(ids, list) and 1 <= len(ids) <= MAX_IDS and len(ids) == len(set(ids)) and
                all(isinstance(value, str) and content_id.fullmatch(value) for value in ids),
                "contentIds must list 1-64 distinct content IDs")
        for variant in variants.values():
            validate_record({**variant, "contentIds": ids})
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


def generate(root, check=False):
    config = load_config(root)
    validate_record = validator(config.KINDS, config.ARTWORK, {rule[1] for rule in config.COMPANION.values()},
                                getattr(config, "MACHINE_OPTIONS", None), getattr(config, "LAUNCH_FIELDS", None))
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
        for disk, variants in entry.get("diskVariants", {}).items():
            own.setdefault(disk, {})["variants"] = variants
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
        for candidate in (entry["title"], *entry.get("aliases", []),
                          *(group.rsplit(":", 1)[-1] for group in entry.get("sourceGroups", []))):
            normalized = lookup_name(candidate)
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
    documents = {**own_documents, "base-v1.json": {"schemaVersion": 1, "games": {}}, "manifest-v1.json": manifest,
                 "name-index-v1.json": name_index, "system-v1.json": system,
                 **{f"shards/{prefix}.json": {"schemaVersion": 1, "games": records}
                    for prefix, records in shards.items()}}
    placeholder = f"sha256-{sorted(config.KINDS)[0]}-v1:" + "0" * 64
    pack = compact({**catalog_documents.build_pack(source, name_index, validate_record, placeholder),
                    "coreVersion": CORE_VERSION, **({"documents": own_documents} if own_documents else {})})
    outputs = {"catalog/source-v1.json": compact(source), "catalog/core-v3.json": pack,
               "catalog/core-v3.meta.json": (json.dumps({"schemaVersion": 1, "archive": "core-v3.json",
                   "sha256": hashlib.sha256(pack).hexdigest(), "size": len(pack)}, separators=(",", ":")) + "\n").encode("utf-8")}
    for path, content in outputs.items():
        if check:
            require((root / path).read_bytes() == content, f"Stale {path}")
        else:
            (root / path).parent.mkdir(parents=True, exist_ok=True)
            (root / path).write_bytes(content)
    optional = {"schemaVersion": 1, "games": {}, "nameIndex": name_index}
    # Kept out of the name index and the update pack: they reach a build only through data.nsfw.
    adult = {"name-index-v1.json": {"games": {key: {"description": entry["adultDescription"],
                                                    **({"descriptions": entry["adultDescriptions"]}
                                                       if entry.get("adultDescriptions") else {})}
             for key, entry in sorted(research["games"].items()) if entry.get("adultDescription")}}}
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
