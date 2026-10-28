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
  search     <language> Wikipedia's top search hits for the name

"-" marks nothing found. None of these is proof alone: a name can lead to an article about something
else (a common word, another game of the same name, a company's later owner). Use a form only when
its article is about the thing the description names; with nothing confirmed, keep the Latin name.
"""

import argparse
import json
import re
import urllib.parse
import urllib.request

AGENT = "KairoCatalog/1.0 (https://github.com/MrJackSpade)"
BATCH = 50
HITS = 3


def query(language, params):
    url = f"https://{language}.wikipedia.org/w/api.php?{urllib.parse.urlencode({**params, 'format': 'json'})}"
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


def forms(language, names):
    english = resolve("en", names, {"prop": "langlinks", "lllang": language, "lllimit": "max"})
    native = resolve(language, names, {})
    rows = {}
    for name in names:
        page = english[name]
        links = page.get("langlinks", []) if page else []
        linked = f"{page['title']} -> {bare(links[0]['*'])}" if links else (f"{page['title']} -> -" if page else "-")
        direct = native[name]["title"] if native[name] else "-"
        hits = query(language, {"action": "query", "list": "search", "srsearch": name,
                                "srlimit": HITS})["query"]["search"]
        rows[name] = (linked, direct, " | ".join(hit["title"] for hit in hits) or "-")
    return rows


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
    for name, (linked, direct, search) in forms(args.language, list(dict.fromkeys(names))).items():
        print(f"{name}\tlinked: {linked}\tdirect: {direct}\tsearch: {search}")


if __name__ == "__main__":
    main()
