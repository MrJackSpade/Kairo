import json
import unittest

import fireworks_translate

ENGLISH = {"a:1": "A long space adventure across many worlds.", "a:2": "A racing game with twelve tracks."}


class FireworksTranslateTest(unittest.TestCase):
    def test_translations_in_the_language_pass(self):
        fireworks_translate.check("ja", ENGLISH, {"a:1": "多くの世界をめぐる長い宇宙の冒険。", "a:2": "十二のコースがあるレースゲーム。"})
        fireworks_translate.check("es", ENGLISH, {"a:1": "Una larga aventura espacial por muchos mundos.",
                                                  "a:2": "Un juego de carreras con doce pistas."})

    def test_the_english_back_fails(self):
        with self.assertRaises(SystemExit):
            fireworks_translate.check("es", ENGLISH, {"a:1": ENGLISH["a:1"], "a:2": "Un juego de carreras con doce pistas."})

    def test_a_translation_without_the_script_fails(self):
        with self.assertRaises(SystemExit):
            fireworks_translate.check("ru", ENGLISH, {"a:1": "Sorry, I can't help with that request here.",
                                                      "a:2": "Гоночная игра с двенадцатью трассами."})

    def test_a_far_shorter_translation_fails(self):
        with self.assertRaises(SystemExit):
            fireworks_translate.check("de", ENGLISH, {"a:1": "Nein.", "a:2": "Ein Rennspiel mit zwölf Strecken."})

    def test_the_started_answer_completes_into_the_response_file(self):
        examples = {"a:1": {"en": ENGLISH["a:1"], "text": "Una larga aventura."}}
        start = fireworks_translate.started(examples)
        answer = json.loads(start + ' "a:2": {\n  "text": "Un juego."\n }' + fireworks_translate.CLOSE)
        self.assertEqual(answer, {"descriptions": {"a:1": {"text": "Una larga aventura."}, "a:2": {"text": "Un juego."}},
                                  "schemaVersion": 1})

    def test_the_request_is_the_file_without_translations(self):
        messages = fireworks_translate.conversation("es", False, {"a:1": {"en": ENGLISH["a:1"], "text": "x"}},
                                                    {"a:2": ENGLISH["a:2"]}, {})
        self.assertEqual(json.loads(messages[1]["content"]),
                         {"descriptions": {"a:1": {"en": ENGLISH["a:1"]}, "a:2": {"en": ENGLISH["a:2"]}}, "schemaVersion": 1})


if __name__ == "__main__":
    unittest.main()
