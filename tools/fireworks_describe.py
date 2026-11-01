#!/usr/bin/env python3
"""Write an app's missing catalog descriptions with a model on Fireworks.ai.

  fireworks_describe.py <app root> --platform NAME [--adult] [--mobygames <dump>] [--references <json>] [--model ID]
                        [--batch N] [--workers N] [--limit N] [--retry-skipped]

Sends the games descriptions.py worklist lists (the shown games without a description; with --adult,
the shown adult games without a frank one) with their reference facts, in batches of N, and merges
each answer as descriptions.py add would. FIREWORKS_API_KEY holds the key.

The request: the user message is {"<game key>": the worklist item without its key}; the answer is
{"<game key>": description or null}. A description is written fresh from the facts, never copying
the excerpts' wording; null means the facts hold nothing real to say about the game (a genre, a
company and a year are not enough, catalog-descriptions-no-filler), and the game is recorded in
<app root>/.downloads/descriptions-skipped[-adult]-v1.json so later runs leave it out (--retry-skipped
sends those again). Main descriptions are clean, for every build: an adult game's is written generally,
and its frank description comes from a run with --adult, which only adult builds carry.

Each answer must hold exactly the batch's games, finish without running out of tokens, and give each a
description of 1 to descriptions.MAX_LENGTH characters that is not a refusal, does not copy a run of
COPIED words from its excerpts and, in a main description, uses none of ADULT_WORDS. The first answer
that fails stops the run, naming the game and the check; batches merged before it are kept, and a
rerun picks up from there. Nothing prints a description's text, or any of the facts sent.
"""

import argparse
import json
import os
import re
from concurrent.futures import FIRST_EXCEPTION, ThreadPoolExecutor, wait
from pathlib import Path

import descriptions
from fireworks_translate import MODEL, post

BATCH = 10
WORKERS = 8
MAX_TOKENS = 32768
# Words a description must not share in a row with an excerpt it was written from.
COPIED = 8
# Main descriptions ship in every build, Play's included (catalog-descriptions-no-adult-wording).
ADULT_WORDS = re.compile(r"\b(adult|erotic\w*|hentai|porn\w*|sex\w*|nud\w+|naked|explicit|lewd|"
                         r"h-scene\w*|fetish\w*|strip(?:-?tease|ping|per)\w*|18\+|r-18|x-rated|lolicon)\b", re.IGNORECASE)
REFUSAL = re.compile(r"\b(I can(no|')t|I'm sorry|I am sorry|as an AI|I won't|I will not)\b", re.IGNORECASE)

BRIEF = """You write the descriptions in a catalog of {platform} games, shown on each game's page in a game library app.
You are given a JSON object of games by key, each with the facts known about it: "title", and where known \
"aliases", "year", "developer", "publisher", "genres", "tags", "files" (the names of its disc or disk images) and \
excerpts of other catalogs' text about it ("launchbox", "mobygames", "reference"). Answer with a JSON object of \
the same keys, each holding the game's description, or null. Nothing else.

A description is one short paragraph of plain English, at most 380 characters: what the game is and what the \
player does in it. Its premise, setting and story, its kind of play and what sets it apart, as the facts tell them. \
Write it fresh in your own words, as a catalog editor would: never copy or closely paraphrase an excerpt's wording, \
and never start with the game's title followed by "is". State only what the facts support; add nothing you do not \
know from them. Keep names of games, people, companies and characters as the facts write them.

Answer null when the facts say nothing real about the game itself: a genre, a company, a platform or a year alone \
is not enough. Never pad such a game into a sentence like "An action game by Sony."
{rules}"""

MAIN_RULES = """
These descriptions are shown in every build of the app, including the Google Play one. Describe every game \
generally, adult games included: their premise, setting and kind of play, with no sexual content or nudity and no \
words such as adult, erotic, hentai, sex, nude, explicit or lewd. Where the facts say the characters are \
schoolgirls or high-school girls, say so plainly. Never add a content warning."""

ADULT_RULES = """
Every game here is an adult game, and this is its frank description, shown only to players who chose to see \
adult content. Say plainly and factually what its adult content is, as the facts tell it: what the sexual content \
is and how the game presents it. Where the characters involved are underage (schoolgirls, high-school girls, a \
stated age under 18), say so and give the age the facts state. Be factual, never graphic or arousing; never \
refuse and never add a content warning."""


def brief(platform, adult):
    return BRIEF.format(platform=platform, rules=ADULT_RULES if adult else MAIN_RULES)


def skipped_path(root, adult):
    return root / f".downloads/descriptions-skipped{'-adult' if adult else ''}-v1.json"


def read_skipped(root, adult):
    path = skipped_path(root, adult)
    return set(json.loads(path.read_text(encoding="utf-8"))) if path.is_file() else set()


def record_skipped(root, adult, keys):
    if not keys:
        return
    path = skipped_path(root, adult)
    with descriptions.locked(path):
        known = read_skipped(root, adult) | set(keys)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(sorted(known), indent=1) + "\n", encoding="utf-8")


def words(text):
    return re.findall(r"[a-z0-9']+", text.lower())


def copies(text, excerpts):
    """Whether [text] holds a run of COPIED words in a row that one of [excerpts] holds."""
    own = words(text)
    runs = {tuple(own[at:at + COPIED]) for at in range(len(own) - COPIED + 1)}
    for excerpt in excerpts:
        theirs = words(excerpt)
        if any(tuple(theirs[at:at + COPIED]) in runs for at in range(len(theirs) - COPIED + 1)):
            return True
    return False


def ask(model, platform, adult, batch):
    """The model's {"<game key>": description or None} for [batch] ({"<game key>": facts}), checked."""
    first = next(iter(batch))
    answer = post("chat/completions", {
        "model": model, "max_tokens": MAX_TOKENS, "response_format": {"type": "json_object"},
        "messages": [{"role": "system", "content": brief(platform, adult)},
                     {"role": "user", "content": json.dumps(batch, ensure_ascii=False, indent=1)}]})
    choice = answer["choices"][0]
    if choice["finish_reason"] != "stop":
        raise SystemExit(f"the batch from {first} ended with {choice['finish_reason']}, not stop")
    try:
        written = json.loads(choice["message"]["content"])
    except json.JSONDecodeError as error:
        raise SystemExit(f"the batch from {first} is not JSON ({error.msg} at {error.pos})")
    if not isinstance(written, dict) or set(written) != set(batch):
        keys = set(written) if isinstance(written, dict) else set()
        raise SystemExit(f"the batch from {first} answers other games: missing "
                         f"{[key for key in batch if key not in keys][:5]}, extra {len(keys - set(batch))}")
    result = {}
    for key, facts in batch.items():
        text = written[key]
        if text is None:
            result[key] = None
            continue
        if not isinstance(text, str):
            raise SystemExit(f"{key}: the answer is a {type(text).__name__}, not a description or null")
        text = re.sub(r"\s+", " ", text).strip()
        if not 0 < len(text) <= descriptions.MAX_LENGTH:
            raise SystemExit(f"{key}: {len(text)} characters, not 1-{descriptions.MAX_LENGTH}")
        if REFUSAL.search(text):
            raise SystemExit(f"{key}: the description reads as a refusal")
        if copies(text, [facts.get(field, "") for field in ("launchbox", "mobygames", "reference")]):
            raise SystemExit(f"{key}: the description copies {COPIED} words in a row from an excerpt")
        if not adult and ADULT_WORDS.search(text):
            raise SystemExit(f"{key}: the main description uses adult wording")
        result[key] = text
    return result


def describe(root, platform, adult, moby_root, references, model, batch, workers, limit, retry_skipped):
    os.environ["FIREWORKS_API_KEY"]  # fail before any work without the key
    skip = set() if retry_skipped else read_skipped(root, adult)
    items = descriptions.worklist_items(root, moby_root, references, limit, adult, skip)
    print(f"{len(items)} games to describe{' frankly' if adult else ''} with {model.split('/')[-1]} "
          f"({len(skip)} skipped before)", flush=True)
    facts = {item["key"]: {field: value for field, value in item.items() if field != "key"} for item in items}
    keys = list(facts)
    parts = [{key: facts[key] for key in keys[start:start + batch]} for start in range(0, len(keys), batch)]
    written = nothing = 0
    with ThreadPoolExecutor(workers) as pool:
        running = {pool.submit(ask, model, platform, adult, part) for part in parts}
        while running:
            done_now, running = wait(running, return_when=FIRST_EXCEPTION)
            for future in done_now:
                if future.exception():
                    pool.shutdown(cancel_futures=True)
                    raise future.exception()
                result = future.result()
                texts = {key: text for key, text in result.items() if text is not None}
                if texts:
                    descriptions.add_descriptions(root, texts, adult)
                record_skipped(root, adult, [key for key, text in result.items() if text is None])
                written += len(texts)
                nothing += len(result) - len(texts)
                print(f"{written} written, {nothing} with nothing real to say", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("root", type=Path)
    parser.add_argument("--platform", required=True, help='the platform as players know it, such as "FM Towns"')
    parser.add_argument("--adult", action="store_true")
    parser.add_argument("--mobygames", type=Path)
    parser.add_argument("--references", type=Path)
    parser.add_argument("--model", default=MODEL)
    parser.add_argument("--batch", type=int, default=BATCH)
    parser.add_argument("--workers", type=int, default=WORKERS)
    parser.add_argument("--limit", type=int)
    parser.add_argument("--retry-skipped", action="store_true")
    args = parser.parse_args()
    describe(args.root.resolve(), args.platform, args.adult, args.mobygames, args.references, args.model, args.batch,
             args.workers, args.limit, args.retry_skipped)


if __name__ == "__main__":
    main()
