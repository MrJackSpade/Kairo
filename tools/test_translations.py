import translations

ENGLISH = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="title">Controls</string>
    <!-- %1$s is the game's title. -->
    <string name="foot">%1$s uses its own mapping.</string>
    <string name="brand" translatable="false">KAIRO</string>
    <plurals name="keys">
        <item quantity="one">Save %1$d key</item>
        <item quantity="other">Save %1$d keys</item>
    </plurals>
</resources>
"""


def setup(tmp_path, english=ENGLISH):
    values = tmp_path / "values"
    values.mkdir(exist_ok=True)
    (values / "strings.xml").write_text(english, encoding="utf-8")
    return tmp_path


def copy(res, language="es"):
    return res / f"values-{language}" / "strings.xml"


def test_sync_copies_every_translatable_string_with_its_english_and_notes(tmp_path):
    res = setup(tmp_path)
    translations.sync(res)
    text = copy(res).read_text(encoding="utf-8")
    assert "<!-- %1$s is the game's title. -->\n    <!-- EN: %1$s uses its own mapping. -->" in text
    assert '<string name="foot">%1$s uses its own mapping.</string>' in text
    assert "brand" not in text
    # Spanish chooses among one, many and other; many starts from the English other.
    assert '<item quantity="many">Save %1$d keys</item>' in text
    assert 'quantity="few"' not in text
    assert 'quantity="few"' in copy(res, "ru").read_text(encoding="utf-8")
    assert translations.check(res) == 0


def test_sync_keeps_translations_and_check_reports_changed_english(tmp_path):
    res = setup(tmp_path)
    translations.sync(res)
    target = copy(res)
    target.write_text(target.read_text(encoding="utf-8")
                      .replace(">Controls</string>", ">Controles</string>"), encoding="utf-8")
    setup(tmp_path, ENGLISH.replace(">Controls<", ">Game controls<"))
    translations.sync(res)
    text = target.read_text(encoding="utf-8")
    assert "<!-- EN: Controls -->\n    <string name=\"title\">Controles</string>" in text
    stale = translations.entries(translations.parse(target))[0]
    assert stale.english[""] == "Controls"


def test_check_fails_on_changed_arguments_and_missing_quantities(tmp_path, capsys):
    res = setup(tmp_path)
    translations.sync(res)
    target = copy(res)
    target.write_text(target.read_text(encoding="utf-8")
                      .replace(">%1$s uses its own mapping.</string>", ">Usa su propio mapeo.</string>")
                      .replace('        <!-- EN: Save %1$d keys -->\n        <item quantity="many">Save %1$d keys</item>\n', ""),
                      encoding="utf-8")
    assert translations.check(res) == 2
    out = capsys.readouterr().out
    assert "foot has [] where the English has ['%1$s']" in out
    assert "keys needs exactly the quantities one, many, other" in out


def item(res, name, text, language="es", quantity=""):
    english = {"title": "Controls", "foot": "%1$s uses its own mapping.", "keys": "Save %1$d keys"}[name]
    if quantity == "one":
        english = "Save %1$d key"
    return {"res": str(res), "file": "strings.xml", "language": language, "name": name,
            "quantity": quantity, "english": english, "text": text}


def test_apply_writes_a_translation_and_export_no_longer_lists_it(tmp_path):
    res = setup(tmp_path)
    translations.sync(res)
    names = {(i["language"], i["name"], i["quantity"]) for i in translations.pending(res)}
    assert ("es", "title", "") in names and ("ru", "keys", "few") in names
    translations.apply([item(res, "title", "Controles"), item(res, "keys", "Guardar %1$d teclas", quantity="other")])
    text = copy(res).read_text(encoding="utf-8")
    assert "<!-- EN: Controls -->\n    <string name=\"title\">Controles</string>" in text
    names = {(i["language"], i["name"], i["quantity"]) for i in translations.pending(res)}
    assert ("es", "title", "") not in names and ("es", "keys", "other") not in names
    assert ("es", "foot", "") in names


def test_same_keeps_the_english_on_purpose_until_the_english_changes(tmp_path, capsys):
    res = setup(tmp_path)
    translations.sync(res)
    translations.apply([item(res, "title", "Controls", language="de")])
    assert "<!-- SAME: Controls -->\n    <string name=\"title\">Controls</string>" in copy(res, "de").read_text(encoding="utf-8")
    assert ("de", "title", "") not in {(i["language"], i["name"], i["quantity"]) for i in translations.pending(res)}
    translations.sync(res)
    assert "SAME: Controls" in copy(res, "de").read_text(encoding="utf-8")
    setup(tmp_path, ENGLISH.replace(">Controls<", ">Game controls<"))
    translations.sync(res)
    capsys.readouterr()
    translations.check(res)
    assert "values-de" + __import__("os").sep + "strings.xml: title: the English changed" in capsys.readouterr().out


def test_apply_refuses_an_unescaped_apostrophe_and_changed_english(tmp_path):
    res = setup(tmp_path)
    translations.sync(res)
    import pytest
    with pytest.raises(SystemExit, match="escape"):
        translations.apply([item(res, "title", "l'app")])
    stale = item(res, "title", "Controles")
    stale["english"] = "Old controls"
    with pytest.raises(SystemExit, match="export again"):
        translations.apply([stale])
