#!/usr/bin/env python3
"""How a language writes a name, from Wikipedia's links between languages.

  name_forms.py <language> <name> [<name> ...]
  name_forms.py <language> --file <names.txt>      one name per line

For each English name (a game, series, company, person, character, place or work), finds the English
Wikipedia article of that name (following redirects) and prints the title of the same article in
<language>'s Wikipedia (ja, ko, ...), as tab-separated: name, English article, form. "-" marks a name
with no English article or no article in that language: it has no established form there.

A name can match an article about something else (a common word, another game of the same name):
check the English article printed beside each form before using it.
"""

import argparse
import json
import re
import urllib.parse
import urllib.request

API = "https://en.wikipedia.org/w/api.php"
AGENT = "KairoCatalog/1.0 (https://github.com/MrJackSpade)"
BATCH = 50


def query(params):
    request = urllib.request.Request(f"{API}?{urllib.parse.urlencode({**params, 'format': 'json'})}",
                                     headers={"User-Agent": AGENT})
    with urllib.request.urlopen(request) as response:
        return json.load(response)


def forms(language, names):
    result = {}
    for start in range(0, len(names), BATCH):
        chunk = names[start:start + BATCH]
        data = query({"action": "query", "titles": "|".join(chunk), "redirects": "1",
                      "prop": "langlinks", "lllang": language, "lllimit": "max"})["query"]
        # The title each name resolved to: normalized (case, underscores), then through redirects.
        resolved = {name: name for name in chunk}
        for step in ("normalized", "redirects"):
            moves = {item["from"]: item["to"] for item in data.get(step, [])}
            resolved = {name: moves.get(title, title) for name, title in resolved.items()}
        pages = {page["title"]: page for page in data["pages"].values()}
        for name, title in resolved.items():
            page = pages.get(title, {})
            if "missing" in page or "invalid" in page or not page:
                result[name] = ("-", "-")
                continue
            links = page.get("langlinks", [])
            # "잭슨 (비디오 게임)": the qualifier only tells articles apart; the name is before it.
            form = re.sub(r"\s*[(（][^()（）]*[)）]$", "", links[0]["*"]) if links else "-"
            result[name] = (title, form)
    return result


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
    for name, (title, form) in forms(args.language, list(dict.fromkeys(names))).items():
        print(f"{name}\t{title}\t{form}")


if __name__ == "__main__":
    main()
