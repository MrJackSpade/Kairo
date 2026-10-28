"""Keeps each language's copy of the Android string resources beside the English, for translators.

Every values/strings*.xml has a copy in values-<language>/ under the same name, holding every
translatable string. Above each string is an `EN:` comment: the English that string translates,
as it was when it was translated, with the English file's notes on its arguments. A string not yet
translated holds the English, so the app shows English until it is.

  python shared/tools/translations.py sync [res ...]   create missing copies, add new strings, drop removed ones
  python shared/tools/translations.py check [res ...]  report what needs a translator, and fail on broken strings

`res` is a res directory (such as frontend/src/main/res); without one, every module's src/main/res in
the current checkout, the shared submodule excepted. Sync never touches a translation or its EN
comment: when the English changes, check reports the string as stale until a translator updates
both. Check fails on what would break the app or misinform a translator: a string whose format
arguments differ from the English, a missing or extra string, or a plural without its language's
quantities.
"""
import argparse
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from xml.sax.saxutils import escape

# Android resource qualifier, the language's name, and the plural quantities Android selects for it.
LANGUAGES = {
    "es": ("Spanish", ["one", "many", "other"]),
    "pt-rBR": ("Portuguese (Brazil)", ["one", "many", "other"]),
    "de": ("German", ["one", "other"]),
    "ru": ("Russian", ["one", "few", "many", "other"]),
    "ja": ("Japanese", ["other"]),
    "ko": ("Korean", ["other"]),
    "ar": ("Arabic", ["zero", "one", "two", "few", "many", "other"]),
    "in": ("Indonesian", ["other"]),
}

EN = "EN:"
FORMAT = re.compile(r"%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z%]")


def parse(path):
    return ET.parse(path, parser=ET.XMLParser(target=ET.TreeBuilder(insert_comments=True))).getroot()


def is_comment(node):
    return node.tag is ET.Comment


class Entry:
    """One string or plural of a file: its notes (comments above it) and its text by quantity ("" for a string)."""

    def __init__(self, name, plural, notes, texts, english=None):
        self.name = name
        self.plural = plural
        self.notes = notes
        self.texts = texts
        # The EN comments, by quantity: the English this entry translates.
        self.english = english if english is not None else {}


def text(node):
    return node.text or ""


def entries(root):
    """The file's translatable entries in order, each with the comments directly above it."""
    found, notes = [], []
    for node in root:
        if is_comment(node):
            notes.append(node.text.strip())
            continue
        if node.tag in ("string", "plurals") and node.get("translatable") != "false":
            name = node.get("name")
            if node.tag == "string":
                english = {"": n[len(EN):].strip() for n in notes if n.startswith(EN)}
                kept = [n for n in notes if not n.startswith(EN)]
                found.append(Entry(name, False, kept, {"": text(node)}, english))
            else:
                texts, english, pending = {}, {}, None
                for item in node:
                    if is_comment(item):
                        if item.text.strip().startswith(EN):
                            pending = item.text.strip()[len(EN):].strip()
                        continue
                    quantity = item.get("quantity")
                    texts[quantity] = text(item)
                    if pending is not None:
                        english[quantity] = pending
                    pending = None
                kept = [n for n in notes if not n.startswith(EN)]
                found.append(Entry(name, True, kept, texts, english))
        notes = []
    return found


def comment(value):
    if "--" in value or value.endswith("-"):
        raise ValueError(f"Cannot put {value!r} in an XML comment")
    return f"<!-- {value} -->"


def english_for(entry, quantity):
    """The English a language's [quantity] of a plural starts from: the same quantity, else other."""
    return entry.texts.get(quantity, entry.texts["other"])


def render(language, source_name, english, existing):
    """The language's copy of a file: [english]'s entries, keeping each translation [existing] holds."""
    title, quantities = LANGUAGES[language]
    have = {entry.name: entry for entry in existing}
    lines = ['<?xml version="1.0" encoding="utf-8"?>',
             comment(f"{title} ({language}): the translation of values/{source_name}. Each EN comment is the "
                     f"English a string translates; update it with the translation. "
                     f"Check with shared/tools/translations.py check."),
             "<resources>"]
    for entry in english:
        old = have.get(entry.name)
        for note in entry.notes:
            lines.append("    " + comment(note))
        if not entry.plural:
            translated = old if old is not None and not old.plural else None
            value = translated.texts[""] if translated else entry.texts[""]
            source = translated.english.get("", value) if translated else entry.texts[""]
            lines.append("    " + comment(f"{EN} {source}"))
            lines.append(f'    <string name="{entry.name}">{escape(value)}</string>')
            continue
        translated = old if old is not None and old.plural else None
        lines.append(f'    <plurals name="{entry.name}">')
        for quantity in quantities:
            if translated and quantity in translated.texts:
                value = translated.texts[quantity]
                source = translated.english.get(quantity, english_for(entry, quantity))
            else:
                value = source = english_for(entry, quantity)
            lines.append("        " + comment(f"{EN} {source}"))
            lines.append(f'        <item quantity="{quantity}">{escape(value)}</item>')
        lines.append("    </plurals>")
    lines.append("</resources>")
    return "\n".join(lines) + "\n"


def sources(res):
    return sorted((res / "values").glob("strings*.xml"))


def sync(res):
    for source in sources(res):
        english = entries(parse(source))
        for language in LANGUAGES:
            target = res / f"values-{language}" / source.name
            existing = entries(parse(target)) if target.exists() else []
            content = render(language, source.name, english, existing)
            if not target.exists() or target.read_text(encoding="utf-8") != content:
                target.parent.mkdir(exist_ok=True)
                target.write_text(content, encoding="utf-8", newline="\n")
                print(f"wrote {target}")


def formats(value):
    return sorted(set(FORMAT.findall(value)) - {"%%"})


def check(res):
    """Prints what needs a translator; returns the number of broken strings."""
    broken = 0
    for source in sources(res):
        english = {entry.name: entry for entry in entries(parse(source))}
        for language, (_, quantities) in LANGUAGES.items():
            target = res / f"values-{language}" / source.name
            if not target.exists():
                print(f"BROKEN  {target}: missing (run sync)")
                broken += 1
                continue
            translated = {entry.name: entry for entry in entries(parse(target))}
            untranslated, stale = 0, []
            for name in translated.keys() - english.keys():
                print(f"BROKEN  {target}: {name} is not in the English (run sync)")
                broken += 1
            for name, entry in english.items():
                theirs = translated.get(name)
                if theirs is None or theirs.plural != entry.plural:
                    print(f"BROKEN  {target}: {name} is missing (run sync)")
                    broken += 1
                    continue
                wanted = [""] if not entry.plural else quantities
                if sorted(theirs.texts) != sorted(wanted):
                    print(f"BROKEN  {target}: {name} needs exactly the quantities {', '.join(wanted)}")
                    broken += 1
                    continue
                for quantity in wanted:
                    source_text = entry.texts[""] if not entry.plural else english_for(entry, quantity)
                    value = theirs.texts[quantity]
                    label = name + (f" ({quantity})" if quantity else "")
                    if formats(value) != formats(source_text):
                        print(f"BROKEN  {target}: {label} has {formats(value)} where the English has {formats(source_text)}")
                        broken += 1
                    if value == source_text:
                        untranslated += 1
                    elif theirs.english.get(quantity) != source_text:
                        stale.append(label)
            for label in stale:
                print(f"STALE   {target}: {label}: the English changed since it was translated")
            if untranslated:
                print(f"TODO    {target}: {untranslated} not translated")
    return broken


def modules(root):
    return sorted(res for res in root.glob("*/src/main/res") if (res / "values").is_dir() and sources(res))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("command", choices=["sync", "check"])
    parser.add_argument("res", nargs="*", type=Path)
    args = parser.parse_args()
    dirs = args.res or modules(Path.cwd())
    if not dirs:
        sys.exit("No res directory with values/strings*.xml here")
    if args.command == "sync":
        for res in dirs:
            sync(res)
        return
    broken = sum(check(res) for res in dirs)
    if broken:
        sys.exit(f"{broken} broken string(s)")


if __name__ == "__main__":
    main()
