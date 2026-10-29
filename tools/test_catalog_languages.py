from catalog_languages import valid_titles


def test_a_game_may_have_its_own_title_in_a_catalog_language():
    assert valid_titles({"ja": "東方封魔録 ～ the Story of Eastern Wonderland"})


def test_titles_need_a_catalog_language_and_text():
    assert not valid_titles({})
    assert not valid_titles({"fr": "Titre"})
    assert not valid_titles({"ja": " "})
    assert not valid_titles({"ja": "x" * 257})
    assert not valid_titles(["ja"])
