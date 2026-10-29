#!/usr/bin/env python3
"""Translate an app's catalog descriptions with a model on Fireworks.ai.

  fireworks_translate.py <app root> [--adult] [--language TAG ...] [--model ID] [--batch N]
                         [--workers N] [--limit N]

Sends what descriptions.py pending would list (each language's untranslated or stale descriptions;
with --adult, the frank adult descriptions) in batches of N to the model, and merges each batch as
descriptions.py translate would. Every language by default; --limit keeps to the first N of each.
FIREWORKS_API_KEY holds the key. The model must be one Fireworks serves serverless with raw_output
(its rendered template), which continuing an answer needs.

The request: the user message is a translation file without its translations,
{"descriptions": {"<game key>": {"en": English}}, "schemaVersion": 1}, holding EXAMPLES translations
the catalog already has and then the batch. The answer is the same file with only the translations,
{"descriptions": {"<game key>": {"text": translation}}, "schemaVersion": 1}, started with the
examples' translations so the model continues an answer in the format it can see: the model's own
chat template renders that start as a finished assistant turn (raw_output), its end is cut off and
the completions endpoint continues it. The brief follows the catalog's naming rule: a game's own title in the
language (research "titles") is used for it; the forms the language's Wikipedia gives each name in
the English (name_forms.py, as descriptions.py names finds them) are offered, for the model to use
only where the article is about the thing named; every other name stays as the English writes it.

Each answer must be the started file completed, with every game (in any order), finish without running
out of tokens, and give each a translation that is not the English, is written in the language's script and
is not far shorter than the English. The first answer that fails stops the run,
naming the game and the check; batches merged before it are kept, and a rerun picks up from there.
Nothing prints a description's text.
"""

import argparse
import json
import os
import random
import re
import threading
import urllib.error
import urllib.request
from concurrent.futures import FIRST_EXCEPTION, ThreadPoolExecutor, wait
from pathlib import Path

import catalog_descriptions
import descriptions
from catalog_languages import LANGUAGES, NAMES

ENDPOINT = "https://api.fireworks.ai/inference/v1"
MODEL = "accounts/fireworks/models/deepseek-v4p1-flash"
EXAMPLES = 10
BATCH = 20
WORKERS = 8
MAX_TOKENS = 32768
# Letters of the script each language is written in, where it is not the Latin alphabet.
SCRIPTS = {"ru": r"[Ѐ-ӿ]", "ja": r"[぀-ヿ一-鿿]", "ko": r"[가-힯]",
           "ar": r"[؀-ۿ]"}
# The shortest a translation may be, as a share of the English's characters: a refusal or a summary
# is far shorter. Japanese and Korean need fewer characters for the same text.
SHORTEST = {"ja": 0.2, "ko": 0.25}
SHORTEST_DEFAULT = 0.5

BRIEF = """You translate descriptions from a video game catalog from English into {language}.{adult}
You are given a catalog file of descriptions, each game's English in "en". Answer with the same file, the same \
games in the same order, each holding only "text": the {language} translation of its "en". Nothing else.

Translate each one completely and faithfully: every fact, in the same register. Do not soften, censor, summarize, \
add warnings or omit anything, and add nothing that is not in the English. Write natural {language}, as a native \
writer of game catalog text would.

Names of games, series, characters, people, companies, places and works:
- "title", in the hints below, is a game's own title in {language}: use it wherever its English names that game.
- "names", in the hints below, lists forms {language} Wikipedia gives names in an English description: "linked" \
is the English Wikipedia article of the name and the title of the same article in {language}; "direct" is the \
{language} article the name itself leads to. Use a form only where that article is clearly about the very thing \
the description names; otherwise ignore it.
- Every other name stays exactly as the English writes it. Never translate, transliterate or invent a form of a name.

Hints, by game key:
{hints}"""

ADULT = (" Each description is a frank, factual summary of an adult game's content, shown only to players who "
         "chose to see adult content.")


def hints_for(language, english, games):
    """{"<game key>": {"title"?, "names"?}} for the descriptions that have any."""
    found = {key: list(dict.fromkeys(descriptions.names_in(text))) for key, text in english.items()}
    leads = descriptions.name_leads(language, {name for names in found.values() for name in names})
    hints = {}
    for key in english:
        hint = {}
        title = games[key].get("titles", {}).get(language)
        if title:
            hint["title"] = title
        names = {name: {"linked": leads[name][0], "direct": leads[name][1]}
                 for name in found[key] if descriptions.native(name, leads[name])}
        if names:
            hint["names"] = names
        if hint:
            hints[key] = hint
    return hints


# The end of the started answer (started()) that the model writes next: the descriptions' close.
CLOSE = '\n },\n "schemaVersion": 1\n}'


def started(examples):
    """The answer's start: the response object with [examples] ({"<game key>": {"en", "text"}})
    written, left open for the next description."""
    whole = json.dumps({"descriptions": {key: {"text": entry["text"]} for key, entry in examples.items()},
                        "schemaVersion": 1}, ensure_ascii=False, indent=1)
    if not whole.endswith(CLOSE):
        raise SystemExit(f"the examples' answer does not end with {CLOSE!r}")
    return whole[:-len(CLOSE)] + ",\n"


def conversation(language, adult, examples, batch, hints):
    """The system and user messages asking for [batch] ({"<game key>": English}) after [examples]."""
    request = {key: {"en": entry["en"]} for key, entry in examples.items()}
    request.update({key: {"en": text} for key, text in batch.items()})
    shown = {key: hints[key] for key in batch if key in hints}
    system = BRIEF.format(language=NAMES[language], adult=ADULT if adult else "",
                          hints=json.dumps(shown, ensure_ascii=False, indent=1) if shown else "(none)")
    return [{"role": "system", "content": system},
            {"role": "user", "content": json.dumps({"descriptions": request, "schemaVersion": 1},
                                                   ensure_ascii=False, indent=1)}]


def post(path, body):
    request = urllib.request.Request(f"{ENDPOINT}/{path}", data=json.dumps(body).encode(), headers={
        "Authorization": f"Bearer {os.environ['FIREWORKS_API_KEY']}", "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(request) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        raise SystemExit(f"Fireworks answered {path} with {error.code}: {error.read().decode(errors='replace')}")


def rendered(model, messages):
    """The token ids of the model's chat template over [messages], as Fireworks renders them."""
    answer = post("chat/completions", {"model": model, "max_tokens": 1, "raw_output": True, "messages": messages})
    raw = answer["choices"][0].get("raw_output")
    if not raw or not raw.get("prompt_token_ids"):
        raise SystemExit(f"{model} gives no raw_output: its template cannot be rendered to continue an answer")
    return raw["prompt_token_ids"]


_tails = {}
_tails_lock = threading.Lock()


def tail(model):
    """How many tokens the template puts after an assistant message's content (its end and the next
    turn's start): the common end of two renderings that differ only in that content."""
    with _tails_lock:
        if model not in _tails:
            one, other = (rendered(model, [{"role": "user", "content": "Hello"}, {"role": "assistant", "content": text}])
                          for text in ("Quiet", "Zebra"))
            common = 0
            while common < min(len(one), len(other)) and one[-1 - common] == other[-1 - common]:
                common += 1
            if common == min(len(one), len(other)):
                raise SystemExit(f"{model}: two assistant contents render the same")
            _tails[model] = common
        return _tails[model]


def continued(model, messages, start):
    """The model's continuation of an assistant answer that begins with [start], through the
    completions endpoint: the template renders the answer as a finished turn in the model's own
    form (a closed or skipped reasoning block, a final channel), and its end is cut off."""
    prompt = rendered(model, messages + [{"role": "assistant", "content": start}])[:-tail(model)]
    choice = post("completions", {"model": model, "prompt": prompt, "max_tokens": MAX_TOKENS})["choices"][0]
    return choice["text"], choice["finish_reason"]


def ask(model, language, adult, examples, batch, hints):
    """The model's {"<game key>": translation} for [batch], checked (check())."""
    start = started(examples)
    text, finish = continued(model, conversation(language, adult, examples, batch, hints), start)
    first = next(iter(batch))
    if finish != "stop":
        raise SystemExit(f"{language}: the batch from {first} ended with {finish}, not stop")
    try:
        answered = json.loads(start + text)
    except json.JSONDecodeError as error:
        raise SystemExit(f"{language}: the batch from {first} does not complete the started answer "
                         f"({error.msg} at {error.pos - len(start)} of the continuation)")
    # Translations pair with their games by key: the order they come back in does not matter.
    wanted = set(examples) | set(batch)
    if not isinstance(answered, dict) or not isinstance(answered.get("descriptions"), dict):
        raise SystemExit(f"{language}: the batch from {first} has no descriptions object")
    # The model sometimes closes the file without its schemaVersion; only the translations are kept.
    if not set(answered) <= {"descriptions", "schemaVersion"}:
        raise SystemExit(f"{language}: the batch from {first} has top-level fields other than descriptions and "
                         f"schemaVersion ({len(answered)} fields, schemaVersion {'present' if 'schemaVersion' in answered else 'missing'})")
    if answered.get("schemaVersion", 1) != 1:
        raise SystemExit(f"{language}: the batch from {first} has schemaVersion of type {type(answered['schemaVersion']).__name__}, not 1")
    keys = set(answered["descriptions"])
    if keys != wanted:
        raise SystemExit(f"{language}: the batch from {first} answers other descriptions: missing "
                         f"{[key for key in batch if key not in keys][:5]}, "
                         f"{len(set(examples) - keys)} examples missing, extra {len(keys - wanted)}")
    translated = {}
    for key in batch:
        entry = answered["descriptions"][key]
        if not isinstance(entry, dict) or set(entry) != {"text"}:
            raise SystemExit(f"{language}: {key}: the answer is not {{\"text\"}}")
        translated[key] = entry["text"]
    check(language, batch, translated)
    return {key: text.strip() for key, text in translated.items()}


def check(language, english, translated):
    """Fails on the first translation that cannot be one of its English (see the module notes)."""
    script = SCRIPTS.get(language)
    shortest = SHORTEST.get(language, SHORTEST_DEFAULT)
    for key, text in translated.items():
        if not isinstance(text, str) or not text.strip():
            raise SystemExit(f"{language}: {key}: no translation")
        text = text.strip()
        if len(text) > descriptions.MAX_TRANSLATION:
            raise SystemExit(f"{language}: {key}: {len(text)} characters, over {descriptions.MAX_TRANSLATION}")
        if text == english[key]:
            raise SystemExit(f"{language}: {key}: the translation is the English")
        if script and not re.search(script, text):
            raise SystemExit(f"{language}: {key}: no {NAMES[language]} letters in the translation")
        if len(text) < shortest * len(english[key]):
            raise SystemExit(f"{language}: {key}: {len(text)} characters for {len(english[key])} of English, "
                             f"under {shortest:.0%}")


def finished(root, language, adult):
    """The language's translations made from the current English: {"<game key>": {"en", "text"}}."""
    source, target = descriptions.translation_paths(root, language, adult)
    english = descriptions.read(source, {"descriptions": {}})["descriptions"]
    done = catalog_descriptions.translations(target)
    outdated = set(catalog_descriptions.stale(english, done))
    return {key: entry for key, entry in done.items() if key in english and key not in outdated}


def pick_examples(done, avoid, rng):
    """EXAMPLES of [done] translations, none of the keys in [avoid]."""
    pool = sorted(key for key in done if key not in avoid)
    if len(pool) < EXAMPLES:
        raise SystemExit(f"only {len(pool)} translations to show as examples; {EXAMPLES} are needed")
    return {key: done[key] for key in sorted(rng.sample(pool, EXAMPLES))}


def batches(items, size):
    keys = list(items)
    return [{key: items[key] for key in keys[start:start + size]} for start in range(0, len(keys), size)]


def translate(root, languages, adult, model, batch, workers, limit):
    os.environ["FIREWORKS_API_KEY"]  # fail before any work without the key
    games = descriptions.read(root / "catalog/research/games-v1.json.gz")["games"]
    rng = random.Random()
    work = []
    for language in languages:
        left, outdated, done, english = descriptions.untranslated(root, language, adult)
        if limit:
            left = dict(list(left.items())[:limit])
        print(f"{language}: {len(left)} to translate with {model.split('/')[-1]}, "
              f"{len(outdated)} of them stale ({done - len(outdated)} of {english} done)", flush=True)
        hints = hints_for(language, left, games)
        examples = finished(root, language, adult)
        work += [(language, part, hints, pick_examples(examples, part, rng)) for part in batches(left, batch)]
    with ThreadPoolExecutor(workers) as pool:
        running = {pool.submit(ask, model, language, adult, examples, part, hints): language
                   for language, part, hints, examples in work}
        while running:
            done_now, _ = wait(running, return_when=FIRST_EXCEPTION)
            for future in done_now:
                language = running.pop(future)
                if future.exception():
                    pool.shutdown(cancel_futures=True)
                    raise future.exception()
                descriptions.store(root, language, future.result(), adult)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("root", type=Path)
    parser.add_argument("--adult", action="store_true")
    parser.add_argument("--language", action="append", choices=LANGUAGES)
    parser.add_argument("--model", default=MODEL)
    parser.add_argument("--batch", type=int, default=BATCH)
    parser.add_argument("--workers", type=int, default=WORKERS)
    parser.add_argument("--limit", type=int)
    args = parser.parse_args()
    translate(args.root.resolve(), args.language or list(LANGUAGES), args.adult, args.model, args.batch,
              args.workers, args.limit)


if __name__ == "__main__":
    main()
