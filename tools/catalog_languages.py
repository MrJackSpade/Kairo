"""The languages a catalog describes games in besides English, by BCP 47 tag.

The app keeps the same list (LayeredGameCatalog.DESCRIPTION_LANGUAGES) and shows the description
in the device's language. Each app's catalog/research/descriptions-<tag>-v1.json holds one.
"""

LANGUAGES = ("es", "pt-BR", "de", "ru", "ja", "ko", "ar", "id")


def valid_titles(value):
    """A game's own titles in other languages: {"<tag>": title}, each 1-256 characters (Kairo#61)."""
    return (isinstance(value, dict) and bool(value) and set(value) <= set(LANGUAGES) and
            all(isinstance(text, str) and 0 < len(text.strip()) <= 256 for text in value.values()))


def valid_translations(value):
    """A game's translated descriptions: {"<tag>": text}, each 1-8000 characters, as the app accepts them."""
    return (isinstance(value, dict) and bool(value) and set(value) <= set(LANGUAGES) and
            all(isinstance(text, str) and 0 < len(text.strip()) <= 8000 for text in value.values()))
