import pytest

import catalog_tags
from catalog_languages import LANGUAGES

PARTS = {
    "Action": {language: f"Action-{language}" for language in LANGUAGES},
    "Platform": {language: f"Platform-{language}" for language in LANGUAGES},
    "Sports": {language: f"Sports-{language}" for language in LANGUAGES},
    "Golf": {language: f"Golf-{language}" for language in LANGUAGES},
}


def test_a_tag_is_translated_part_by_part():
    names = catalog_tags.translated_tags(["Action; Platform", "Sports / Golf"], PARTS)
    assert names["ja"] == {"Action; Platform": "Action-ja; Platform-ja", "Sports / Golf": "Sports-ja / Golf-ja"}


def test_years_show_as_they_are():
    assert catalog_tags.translated_tags(["1994", "Action"], PARTS)["de"] == {"Action": "Action-de"}


def test_every_language_is_given():
    assert set(catalog_tags.translated_tags(["Action"], PARTS)) == set(LANGUAGES)


def test_a_part_the_vocabulary_lacks_fails():
    with pytest.raises(ValueError, match="Racing & Driving"):
        catalog_tags.translated_tags(["Action; Racing & Driving"], PARTS)


def test_the_shipped_vocabulary_has_every_language():
    parts = catalog_tags.load()
    assert parts and all(set(LANGUAGES) <= set(texts) for texts in parts.values())
