"""An app's description files, merged into its research games.

catalog/research/ holds them, each {"schemaVersion": 1, "descriptions": {"<game key>": text}}:

  descriptions-v1.json        original descriptions
  descriptions-adult-v1.json  an adult game's frank description, which replaces its main
                              description only in the adult data part (catalog_parts.py)
  descriptions-<language>-v1.json, descriptions-adult-<language>-v1.json
                              the same translated, for each of catalog_languages.LANGUAGES, each
                              {"en": the English it translates, "text": translation} so a reviewer
                              reads both side by side; a game's translation needs its English
                              description. When the English changes, the translation is stale
                              (stale()) until a translator updates both; it still ships.
"""

import json

from catalog_languages import LANGUAGES


def read(path):
    if not path.is_file():
        return {}
    return json.loads(path.read_text(encoding="utf-8"))["descriptions"]


def translations(path):
    """A language file's {"<game key>": {"en", "text"}}."""
    entries = read(path)
    broken = [key for key, entry in entries.items()
              if not isinstance(entry, dict) or set(entry) != {"en", "text"} or
              not all(isinstance(value, str) and value for value in entry.values())]
    if broken:
        raise SystemExit(f"{path.name}: entries not {{\"en\", \"text\"}}: {sorted(broken)[:5]}")
    return entries


def texts(path):
    """A language file's {"<game key>": translation}."""
    return {key: entry["text"] for key, entry in translations(path).items()}


def stale(english, entries):
    """The keys whose translation was made from English that has since changed."""
    return sorted(key for key, entry in entries.items() if key in english and entry["en"] != english[key])


def apply(research, games):
    """Sets each game's description, adultDescription, descriptions and adultDescriptions."""
    for name, field in (("descriptions-v1.json", "description"), ("descriptions-adult-v1.json", "adultDescription")):
        written = read(research / name)
        unknown = written.keys() - games.keys()
        if unknown:
            raise SystemExit(f"{name}: descriptions for unknown games: {sorted(unknown)[:5]}")
        for key, text in written.items():
            games[key][field] = text
    for kind, english, field in (("", "description", "descriptions"), ("-adult", "adultDescription", "adultDescriptions")):
        for language in LANGUAGES:
            translated = texts(research / f"descriptions{kind}-{language}-v1.json")
            untranslatable = [key for key in translated if not games.get(key, {}).get(english)]
            if untranslatable:
                raise SystemExit(f"descriptions{kind}-{language}-v1.json: translations of no {english}: "
                                 f"{sorted(untranslatable)[:5]}")
            for key, text in translated.items():
                games[key].setdefault(field, {})[language] = text
