"""A catalog's tags (genre, players) in each catalog language (KairoDos#141).

A tag such as "Action; Platform" or "Sports / Golf" is LaunchBox's form: parts joined by "; ", a part
and its kind by " / ". shared/catalog/tags-v1.json gives each part in every language of
catalog_languages.LANGUAGES. A year shows as it is. A tag with a part the vocabulary lacks fails the
build, so a new genre is translated before it ships.
"""
import json
import re
from pathlib import Path

from catalog_languages import LANGUAGES

VOCABULARY = Path(__file__).resolve().parent.parent / "catalog" / "tags-v1.json"
YEAR = re.compile(r"\d{4}")


def load(path=VOCABULARY):
    """The vocabulary's parts, {part: {language: text}}, each in every language."""
    document = json.loads(Path(path).read_text(encoding="utf-8"))
    if document.get("schemaVersion") != 1:
        raise ValueError(f"{path}: schemaVersion is not 1")
    parts = document["parts"]
    for part, texts in parts.items():
        missing = [language for language in LANGUAGES if not texts.get(language, "").strip()]
        if missing:
            raise ValueError(f"{path}: {part!r} has no {', '.join(missing)}")
    return parts


def translate(tag, language, parts):
    """[tag] in [language], part by part."""
    return "; ".join(" / ".join(parts[piece][language] for piece in genre.split(" / "))
                     for genre in tag.split("; "))


def translated_tags(tags, parts=None):
    """{language: {tag: text}} for every tag of [tags] that isn't a year."""
    parts = load() if parts is None else parts
    words = sorted({tag for tag in tags if not YEAR.fullmatch(tag)})
    missing = sorted({piece for tag in words for genre in tag.split("; ") for piece in genre.split(" / ")} - set(parts))
    if missing:
        raise ValueError(f"tags-v1.json has no translation for: {', '.join(missing)}")
    return {language: {tag: translate(tag, language, parts) for tag in words} for language in LANGUAGES}
