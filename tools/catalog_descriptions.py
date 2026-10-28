"""An app's description files, merged into its research games.

catalog/research/ holds them, each {"schemaVersion": 1, "descriptions": {"<game key>": text}}:

  descriptions-v1.json        original descriptions
  descriptions-adult-v1.json  an adult game's frank description, which replaces its main
                              description only in the adult data part (catalog_parts.py)
  descriptions-<language>-v1.json, descriptions-adult-<language>-v1.json
                              the same translated, for each of catalog_languages.LANGUAGES; a game's
                              translation needs its English description
"""

import json

from catalog_languages import LANGUAGES


def read(path):
    if not path.is_file():
        return {}
    return json.loads(path.read_text(encoding="utf-8"))["descriptions"]


def apply(research, games):
    """Sets each game's description, adultDescription, descriptions and adultDescriptions."""
    for name, field in (("descriptions-v1.json", "description"), ("descriptions-adult-v1.json", "adultDescription")):
        texts = read(research / name)
        unknown = texts.keys() - games.keys()
        if unknown:
            raise SystemExit(f"{name}: descriptions for unknown games: {sorted(unknown)[:5]}")
        for key, text in texts.items():
            games[key][field] = text
    for kind, english, field in (("", "description", "descriptions"), ("-adult", "adultDescription", "adultDescriptions")):
        for language in LANGUAGES:
            texts = read(research / f"descriptions{kind}-{language}-v1.json")
            untranslatable = [key for key in texts if not games.get(key, {}).get(english)]
            if untranslatable:
                raise SystemExit(f"descriptions{kind}-{language}-v1.json: translations of no {english}: "
                                 f"{sorted(untranslatable)[:5]}")
            for key, text in texts.items():
                games[key].setdefault(field, {})[language] = text
