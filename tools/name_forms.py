#!/usr/bin/env python3
"""How a language writes a name, from Wikipedia.

  name_forms.py <language> <name> [<name> ...]
  name_forms.py <language> --file <names.txt>      one name per line

For each name (a game, series, company, person, character, place or work) prints, tab-separated:

  name
  linked     the English Wikipedia article of that name (following redirects) -> the title of the
             same article in <language>'s Wikipedia (ja, ko, ...)
  direct     the <language> Wikipedia article the name itself leads to there (an article or a
             redirect of that exact title, such as ja "Wizardry" -> ウィザードリィ)
  search     <language> Wikipedia's top search hits for the name, when neither found an article

"-" marks nothing found. Answers are kept in ~/.cache/kairo-name-forms/<language>.json; delete it to
ask Wikipedia again. None of these is proof alone: a name can lead to an article about something
else (a common word, another game of the same name, a company's later owner). Use a form only when
its article is about the thing the description names; with nothing confirmed, keep the Latin name.
"""

import argparse
import json
import os
import re
import time
import urllib.parse
import urllib.request
from pathlib import Path

AGENT = "KairoCatalog/1.0 (https://github.com/MrJackSpade)"
# Answers already fetched, per language: Wikipedia's search refuses work when asked too often.
CACHE = Path.home() / ".cache/kairo-name-forms"
BATCH = 50
HITS = 3


# Wikipedia asks API clients to send one request at a time and not quickly (it answers 429
# otherwise): this process waits until a second has passed since its last request.
PACE = 1.0
_last = [0.0]


def query(language, params):
    url = f"https://{language}.wikipedia.org/w/api.php?{urllib.parse.urlencode({**params, 'format': 'json'})}"
    wait = _last[0] + PACE - time.monotonic()
    if wait > 0:
        time.sleep(wait)
    _last[0] = time.monotonic()
    with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": AGENT})) as response:
        data = json.load(response)
    if "query" not in data:
        raise SystemExit(f"{language} Wikipedia answered {params} with {data}")
    return data


def bare(title):
    # "잭슨 (비디오 게임)": the qualifier only tells articles apart; the name is before it.
    return re.sub(r"\s*[(（][^()（）]*[)）]$", "", title)


def resolve(language, names, extra):
    """{name: page or None} for the exact titles in <language>'s Wikipedia, through redirects."""
    pages = {}
    for start in range(0, len(names), BATCH):
        chunk = names[start:start + BATCH]
        data = query(language, {"action": "query", "titles": "|".join(chunk), "redirects": "1", **extra})["query"]
        resolved = {name: name for name in chunk}
        for step in ("normalized", "redirects"):
            moves = {item["from"]: item["to"] for item in data.get(step, [])}
            resolved = {name: moves.get(title, title) for name, title in resolved.items()}
        found = {page["title"]: page for page in data["pages"].values()}
        for name, title in resolved.items():
            page = found.get(title)
            pages[name] = None if not page or "missing" in page or "invalid" in page else page
    return pages


# A cached row whose search was not run (a bulk lookup, search=False).
NOT_SEARCHED = "?"


def cached(language, names, search=True):
    """forms() through a per-language cache shared by every caller, so each name is asked once.
    search=False (bulk checks of many names) skips the search lead; a later call that wants it
    runs it then."""
    path = CACHE / f"{language}.json"
    known = json.loads(path.read_text(encoding="utf-8")) if path.is_file() else {}
    def keep(rows):
        # Each batch's answers as they arrive, so a refused request later keeps the ones before
        # it. Another caller may have written meanwhile: merge into the file as it is now.
        current = json.loads(path.read_text(encoding="utf-8")) if path.is_file() else {}
        current.update({name: list(row) for name, row in rows.items()})
        CACHE.mkdir(parents=True, exist_ok=True)
        scratch = path.with_suffix(f".{os.getpid()}.tmp")
        scratch.write_text(json.dumps(current, ensure_ascii=False, indent=0), encoding="utf-8")
        os.replace(scratch, path)
        known.update({name: list(row) for name, row in rows.items()})

    missing = [name for name in names if name not in known or
               search and known[name][2] == NOT_SEARCHED and known[name][0] == "-" and known[name][1] == "-"]
    for start in range(0, len(missing), BATCH):
        forms(language, missing[start:start + BATCH], keep, search)
    return {name: tuple(known[name]) for name in names}


def forms(language, names, keep, search=True):
    english = resolve("en", names, {"prop": "langlinks", "lllang": language, "lllimit": "max"})
    native = resolve(language, names, {})
    rows = {}
    for name in names:
        page = english[name]
        links = page.get("langlinks", []) if page else []
        linked = f"{page['title']} -> {bare(links[0]['*'])}" if links else (f"{page['title']} -> -" if page else "-")
        direct = native[name]["title"] if native[name] else "-"
        # Search only for names neither title found: Wikipedia's search refuses work when busy.
        if links or native[name]:
            found = "-"
        elif not search:
            found = NOT_SEARCHED
        else:
            hits = query(language, {"action": "query", "list": "search", "srsearch": name,
                                    "srlimit": HITS})["query"]["search"]
            found = " | ".join(hit["title"] for hit in hits) or "-"
        rows[name] = (linked, direct, found)
    keep(rows)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("language")
    parser.add_argument("names", nargs="*")
    parser.add_argument("--file")
    args = parser.parse_args()
    names = list(args.names)
    if args.file:
        with open(args.file, encoding="utf-8") as lines:
            names += [line.strip() for line in lines if line.strip()]
    if not names:
        raise SystemExit("no names given")
    for name, (linked, direct, search) in cached(args.language, list(dict.fromkeys(names))).items():
        print(f"{name}\tlinked: {linked}\tdirect: {direct}\tsearch: {search}")


if __name__ == "__main__":
    main()
