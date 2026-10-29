#!/usr/bin/env python3
"""Write an app's original game descriptions in batches.

  descriptions.py worklist <app root> <out.jsonl> [--mobygames <dump>] [--references <json>] [--limit N]
      One line per game the library shows (one with media or that a medium's file name picks,
      not hidden) without a description, with the reference facts for writing one:
      title, aliases, tags, year, developer, publisher, MobyGames genres, and short excerpts of the
      LaunchBox and MobyGames descriptions, or of an app's own references ({"<game key>": text},
      such as KairoDos's eXoDOS text). The excerpts are reference only; catalog descriptions are
      written fresh and never copy their wording.
  descriptions.py add <app root> <batch.json> [--adult]
      Merge {"<game key>": "description"} into catalog/research/descriptions-v1.json (with --adult,
      an adult game's frank description into descriptions-adult-v1.json). Each key must be a game
      in games-v1.json (with --adult, one marked adult) that has no such description yet.

  descriptions.py pending <app root> <language> <out.json> [--adult] [--limit N] [--from KEY] [--to KEY]
      Up to N of the English descriptions (or with --adult, the frank adult descriptions) not yet
      translated into <language> (catalog_languages.LANGUAGES), or whose translation is stale (made
      from English that has since changed), as {"<game key>": "English"};
      --from and --to keep to game keys from KEY up to, not including, KEY, so several workers
      can share a language.
  descriptions.py translate <app root> <language> <batch.json> [--adult]
      Merge {"<game key>": "translation"} into catalog/research/descriptions[-adult]-<language>-v1.json,
      each stored as {"en": the English it translates, "text": translation}. Each key must have that
      English description and no translation yet, or a stale one.
  descriptions.py revise <app root> <language> <out.json> [--adult] [--limit N] [--after KEY]
      Up to N translations, after game key KEY, that still write words in Latin letters, as
      {"<game key>": {"en": "English", "current": "translation"}}, for a pass that rewrites them.
  descriptions.py rewritten <app root> <language> <out.json> [--adult] [--limit N] [--after KEY]
      The same, for the translations that differ from the committed file (git HEAD), with that
      version as "before": {"<game key>": {"en": ..., "before": ..., "current": ...}}.
  descriptions.py names <app root> <language> <out.json> [--adult] [--limit N] [--after KEY]
      Up to N translations, after game key KEY, that keep a name in its English form where the
      language's Wikipedia has an established form of it (name_forms.py): an official localized
      title, a standard exonym. {"<game key>": {"en", "current", "names": {name: [linked, direct]}}},
      each name to check and, where its article is the thing the description names, write natively.
  descriptions.py translate <app root> <language> <batch.json> --replace [--adult]
      Replace existing translations with {"<game key>": "translation"}; each key must be translated.
  descriptions.py stale <app root>
      Each language's count of stale translations, main and adult.

Run research.py afterwards to carry the descriptions into games-v1.json.
"""

import argparse
import contextlib
import html
import json
import os
import re
import subprocess
import time
from pathlib import Path

EXCERPT = 360
MAX_LENGTH = 400
# The longest description the app shows (LayeredGameCatalog.validDescription).
MAX_TRANSLATION = 8000


def read(path, default=None):
    return json.loads(path.read_text(encoding="utf-8")) if path.is_file() else default


def plain(text):
    text = re.sub(r"<[^>]+>", " ", text or "")
    return re.sub(r"\s+", " ", html.unescape(text)).strip()


def excerpt(text):
    text = plain(text)
    return text if len(text) <= EXCERPT else text[:EXCERPT].rsplit(" ", 1)[0] + " …"


def worklist(root, output, moby_root, references_path, limit):
    games = read(root / "catalog/research/games-v1.json")["games"]
    described = read(root / "catalog/research/descriptions-v1.json", {"descriptions": {}})["descriptions"]
    snapshots = {}
    for path in (root / ".downloads/launchbox").glob("*-snapshot.json"):
        snapshots.update(read(path))
    listings = {}
    for path in (moby_root.glob("*-listing.json") if moby_root else ()):
        for game in read(path):
            listings[game["id"]] = game
    references = read(references_path) if references_path else {}
    # The games of a collection (diskCollection) show in the library as well, when the player picks one.
    picked = {member["game"] for entry in games.values() for members in entry.get("diskCollection", {}).values()
              for member in members.values()}
    lines = []
    for key, entry in sorted(games.items()):
        # Only games the library can show: a game no medium identifies, or a hidden one, is never seen.
        shown = entry.get("contentIds") or key in picked
        if key in described or entry.get("description") or not shown or entry.get("hidden"):
            continue
        item = {"key": key, "title": entry["title"]}
        if key in references:
            item["reference"] = excerpt(references[key])
        for field in ("aliases", "tags", "year", "developer", "publisher", "adult"):
            if entry.get(field):
                item[field] = entry[field]
        if entry.get("sourceGroups") and ":local-" in key:
            item["files"] = entry["sourceGroups"][:3]
        launchbox = snapshots.get(entry.get("launchbox", ""), {})
        if launchbox.get("Overview"):
            item["launchbox"] = excerpt(launchbox["Overview"])
        moby_id = entry.get("mobygames")
        if moby_id:
            item["genres"] = listings.get(moby_id, {}).get("genres", [])
            record = read(moby_root / "games" / f"{moby_id}.json", {})
            if record.get("description"):
                item["mobygames"] = excerpt(record["description"])
        lines.append(json.dumps(item, ensure_ascii=False))
        if limit and len(lines) >= limit:
            break
    output.write_text("\n".join(lines) + ("\n" if lines else ""), encoding="utf-8")
    print(f"{len(lines)} games to describe -> {output}")


def add(root, batch_path, adult=False):
    path = root / f"catalog/research/descriptions{'-adult' if adult else ''}-v1.json"
    with locked(path):
        merge_descriptions(root, path, batch_path, adult)


def merge_descriptions(root, path, batch_path, adult):
    games = read(root / "catalog/research/games-v1.json")["games"]
    document = read(path, {"schemaVersion": 1, "descriptions": {}})
    batch = read(batch_path)
    for key, text in batch.items():
        if key not in games:
            raise SystemExit(f"unknown game {key}")
        if adult and not games[key].get("adult"):
            raise SystemExit(f"{key} is not an adult game")
        if key in document["descriptions"]:
            raise SystemExit(f"{key} already has a description")
        if not isinstance(text, str) or not 0 < len(text.strip()) <= MAX_LENGTH or text != text.strip():
            raise SystemExit(f"{key}: description must be 1-{MAX_LENGTH} characters without outer spaces")
    document["descriptions"].update(batch)
    document["descriptions"] = dict(sorted(document["descriptions"].items()))
    path.write_text(json.dumps(document, ensure_ascii=False, indent=1, sort_keys=True) + "\n", encoding="utf-8")
    print(f"{len(batch)} added; {len(document['descriptions'])} of {len(games)} games described")


def translation_paths(root, language, adult):
    from catalog_languages import LANGUAGES
    if language not in LANGUAGES:
        raise SystemExit(f"{language} is not one of {', '.join(LANGUAGES)}")
    kind = "-adult" if adult else ""
    research = root / "catalog/research"
    if not (research / "descriptions-v1.json").is_file():
        raise SystemExit(f"{root} is not an app root: it has no catalog/research/descriptions-v1.json")
    return research / f"descriptions{kind}-v1.json", research / f"descriptions{kind}-{language}-v1.json"


def done_translations(target):
    """A language file's {"<game key>": {"en", "text"}}, empty when it doesn't exist yet."""
    import catalog_descriptions
    return catalog_descriptions.translations(target)


def done_texts(target):
    return {key: entry["text"] for key, entry in done_translations(target).items()}


def pending(root, language, output, adult, limit, start=None, end=None):
    import catalog_descriptions
    source, target = translation_paths(root, language, adult)
    english = read(source, {"descriptions": {}})["descriptions"]
    done = done_translations(target)
    outdated = set(catalog_descriptions.stale(english, done))
    left = {key: text for key, text in sorted(english.items()) if (key not in done or key in outdated) and
            (start is None or key >= start) and (end is None or key < end)}
    batch = dict(list(left.items())[:limit] if limit else left.items())
    output.write_text(json.dumps(batch, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"{len(batch)} of {len(left)} to translate, {len(outdated)} of them stale "
          f"({len(done) - len(outdated)} of {len(english)} done) -> {output}")


def report_stale(root):
    import catalog_descriptions
    from catalog_languages import LANGUAGES
    for adult in (False, True):
        for language in LANGUAGES:
            source, target = translation_paths(root, language, adult)
            if target.is_file():
                english = read(source, {"descriptions": {}})["descriptions"]
                print(f"{target.name}: {len(catalog_descriptions.stale(english, done_translations(target)))} stale")


def revise(root, language, output, adult, limit, after):
    source, target = translation_paths(root, language, adult)
    english = read(source, {"descriptions": {}})["descriptions"]
    done = done_texts(target)
    latin = {key: text for key, text in sorted(done.items()) if re.search(r"[A-Za-z]", text)}
    left = [key for key in latin if after is None or key > after]
    batch = {key: {"en": english[key], "current": latin[key]} for key in (left[:limit] if limit else left)}
    output.write_text(json.dumps(batch, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"{len(batch)} of {len(left)} left after {after} ({len(latin)} of {len(done)} use Latin letters) -> {output}")


def rewritten(root, language, output, adult, limit, after):
    source, target = translation_paths(root, language, adult)
    english = read(source, {"descriptions": {}})["descriptions"]
    done = done_texts(target)
    committed = subprocess.run(["git", "show", f"HEAD:{target.relative_to(root).as_posix()}"], cwd=root,
                               check=True, stdout=subprocess.PIPE).stdout
    before = {key: entry["text"] for key, entry in json.loads(committed)["descriptions"].items()}
    changed = [key for key in sorted(done) if done[key] != before.get(key)]
    left = [key for key in changed if after is None or key > after]
    batch = {key: {"en": english[key], "before": before.get(key), "current": done[key]}
             for key in (left[:limit] if limit else left)}
    output.write_text(json.dumps(batch, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"{len(batch)} of {len(left)} left after {after} ({len(changed)} of {len(done)} rewritten) -> {output}")


@contextlib.contextmanager
def locked(path):
    """Hold <path>.lock while reading and rewriting <path>, so workers sharing a file keep each other's work."""
    lock = path.with_name(path.name + ".lock")
    while True:
        try:
            handle = os.open(lock, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
            break
        except FileExistsError:
            print(f"waiting for {lock.name} (delete it if no translate is running)", flush=True)
            time.sleep(1)
    try:
        yield
    finally:
        os.close(handle)
        os.remove(lock)


# A name in an English description: capitalized words (with the small words titles hold), or a
# number starting a title.
NAME = re.compile(r"(?:[A-Z][\w'’!&-]*|\d[\w'’!&-]*)(?:[ :-]+(?:of|the|de|du|la|le|von|van|vs|&|'n'?|n'|[A-Z0-9][\w\'’!&-]*))*")
SMALL = re.compile(r"(?:[ :-]+(?:of|the|de|du|la|le|von|van|vs|&|'n'?|n'))+$")
# The Wikipedia that writes each catalog language.
WIKI = {"pt-BR": "pt"}


def names_in(text):
    found = []
    for match in NAME.finditer(text):
        name = SMALL.sub("", match.group(0)).strip(" :-.")
        # A sentence's first word may be an ordinary verb before the name ("Explore Ghosts 'n
        # Goblins"): offer the name without it too. Only names the translation still holds count.
        opening = match.start() == 0 or text[:match.start()].rstrip().endswith((".", "!", "?"))
        rest = name.split(" ", 1)[1] if opening and " " in name else ""
        found += [candidate for candidate in (name, rest) if len(candidate) >= 3]
    return found


def names(root, language, output, adult, limit, after):
    import name_forms
    source, target = translation_paths(root, language, adult)
    english = read(source, {"descriptions": {}})["descriptions"]
    done = done_texts(target)
    keys = [key for key in sorted(done) if after is None or key > after]
    def kept(name, text):
        return re.search(r"(?<!\w)" + re.escape(name) + r"(?!\w)", text) is not None
    candidates = {key: [name for name in dict.fromkeys(names_in(english[key])) if kept(name, done[key])] for key in keys}
    leads = name_forms.cached(WIKI.get(language, language),
                              sorted({name for found in candidates.values() for name in found}), search=False)

    def native(name):
        """The form the language's Wikipedia gives the name, when it differs from the English. An
        English article of another title (a redirect to something broader, such as a year to its
        millennium) says nothing about the name itself; a plain number is not a name."""
        if re.fullmatch(r"[\d\s.,x-]+", name):
            return []
        linked, direct, _ = leads[name]
        title, _, form = linked.partition(" -> ")
        forms = [form if re.sub(r"\s*\([^()]*\)$", "", title).casefold() == name.casefold() else "-", direct]
        return [form for form in forms if form and form != "-" and form.casefold() != name.casefold()]

    flagged = {}
    for key in keys:
        found = {name: list(leads[name][:2]) for name in candidates[key] if native(name)}
        if found:
            flagged[key] = {"en": english[key], "current": done[key], "names": found}
    batch = dict(list(flagged.items())[:limit] if limit else flagged.items())
    output.write_text(json.dumps(batch, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"{len(batch)} of {len(flagged)} translations to check after {after} -> {output}")


def translate(root, language, batch_path, adult, replace=False):
    source, target = translation_paths(root, language, adult)
    with locked(target):
        merge(target, source, language, batch_path, replace)


def merge(target, source, language, batch_path, replace):
    import catalog_descriptions
    english = read(source, {"descriptions": {}})["descriptions"]
    document = {"schemaVersion": 1, **read(target, {}), "descriptions": done_translations(target)}
    outdated = set(catalog_descriptions.stale(english, document["descriptions"]))
    batch = read(batch_path)
    for key, text in batch.items():
        if key not in english:
            raise SystemExit(f"{key} has no English description in {source.name}")
        if replace and key not in document["descriptions"]:
            raise SystemExit(f"{key} has no translation in {target.name} to replace")
        if not replace and key in document["descriptions"] and key not in outdated:
            raise SystemExit(f"{key} is already translated in {target.name}")
        if not isinstance(text, str) or not 0 < len(text.strip()) <= MAX_TRANSLATION or text != text.strip():
            raise SystemExit(f"{key}: a translation must be 1-{MAX_TRANSLATION} characters without outer spaces")
    # Each translation keeps the English it was made from, beside it, for reviewers and stale().
    document["descriptions"].update({key: {"en": english[key], "text": text} for key, text in batch.items()})
    document["descriptions"] = dict(sorted(document["descriptions"].items()))
    target.write_text(json.dumps(document, ensure_ascii=False, indent=1, sort_keys=True) + "\n", encoding="utf-8")
    print(f"{len(batch)} {'replaced' if replace else 'added'}; "
          f"{len(document['descriptions'])} of {len(english)} translated into {language}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command", required=True)
    work = commands.add_parser("worklist")
    work.add_argument("root", type=Path)
    work.add_argument("output", type=Path)
    work.add_argument("--mobygames", type=Path)
    work.add_argument("--references", type=Path)
    work.add_argument("--limit", type=int)
    merge = commands.add_parser("add")
    merge.add_argument("root", type=Path)
    merge.add_argument("batch", type=Path)
    merge.add_argument("--adult", action="store_true")
    todo = commands.add_parser("pending")
    todo.add_argument("root", type=Path)
    todo.add_argument("language")
    todo.add_argument("output", type=Path)
    todo.add_argument("--adult", action="store_true")
    todo.add_argument("--limit", type=int)
    todo.add_argument("--from", dest="start")
    todo.add_argument("--to", dest="end")
    done = commands.add_parser("translate")
    done.add_argument("root", type=Path)
    done.add_argument("language")
    done.add_argument("batch", type=Path)
    done.add_argument("--adult", action="store_true")
    done.add_argument("--replace", action="store_true")
    redo = commands.add_parser("revise")
    redo.add_argument("root", type=Path)
    redo.add_argument("language")
    redo.add_argument("output", type=Path)
    redo.add_argument("--adult", action="store_true")
    redo.add_argument("--limit", type=int)
    redo.add_argument("--after")
    check = commands.add_parser("names")
    check.add_argument("root", type=Path)
    check.add_argument("language")
    check.add_argument("output", type=Path)
    check.add_argument("--adult", action="store_true")
    check.add_argument("--limit", type=int)
    check.add_argument("--after")
    audit = commands.add_parser("rewritten")
    audit.add_argument("root", type=Path)
    audit.add_argument("language")
    audit.add_argument("output", type=Path)
    audit.add_argument("--adult", action="store_true")
    audit.add_argument("--limit", type=int)
    audit.add_argument("--after")
    outdated = commands.add_parser("stale")
    outdated.add_argument("root", type=Path)
    args = parser.parse_args()
    if args.command == "stale":
        report_stale(args.root.resolve())
    elif args.command == "worklist":
        worklist(args.root.resolve(), args.output, args.mobygames, args.references, args.limit)
    elif args.command == "add":
        add(args.root.resolve(), args.batch, args.adult)
    elif args.command == "pending":
        pending(args.root.resolve(), args.language, args.output, args.adult, args.limit, args.start, args.end)
    elif args.command == "revise":
        revise(args.root.resolve(), args.language, args.output, args.adult, args.limit, args.after)
    elif args.command == "names":
        names(args.root.resolve(), args.language, args.output, args.adult, args.limit, args.after)
    elif args.command == "rewritten":
        rewritten(args.root.resolve(), args.language, args.output, args.adult, args.limit, args.after)
    else:
        translate(args.root.resolve(), args.language, args.batch, args.adult, args.replace)


if __name__ == "__main__":
    main()
