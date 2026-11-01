#!/usr/bin/env python3
"""Write an app's missing catalog descriptions with a model on Fireworks.ai, as fireworks_translate.py
translates them.

  fireworks_describe.py <app root> [--adult] [--mobygames <dump>] [--references <json>] [--model ID]
                        [--batch N] [--workers N] [--limit N]

Sends the games descriptions.py worklist lists (the shown games without a description; with --adult,
the shown adult games without a frank one) in batches of N to the model, and merges each batch as
descriptions.py add would. FIREWORKS_API_KEY holds the key.

The request is fireworks_translate.py's: the user message is a description file without its
descriptions, {"descriptions": {"<game key>": {"facts": ...}}, "schemaVersion": 1}, each game's
facts as the worklist gives them (descriptions.game_facts), holding EXAMPLES games the catalog has
described already and then the batch. The answer is the same file with only the descriptions,
{"descriptions": {"<game key>": {"text": description}}, ...}, started with the examples' own
descriptions (main ones, or with --adult the frank ones), which the model continues. A game whose
facts say nothing real about it is answered null and left undescribed.

Each answer must be the started file completed, with every game, finish without running out of
tokens, and give each a description of 1 to descriptions.MAX_LENGTH characters or null. The first
answer that fails stops the run, naming the game and the check; batches merged before it are kept,
and a rerun picks up from there. Nothing prints a description's text.
"""

import argparse
import json
import os
import random
from concurrent.futures import FIRST_EXCEPTION, ThreadPoolExecutor, wait
from pathlib import Path

import descriptions
from fireworks_translate import BATCH, EXAMPLES, MODEL, WORKERS, batches, continued, started

BRIEF = """You write the descriptions in a video game catalog.{adult}
You are given a catalog file of games, each with the facts known about it in "facts". Answer with the same file, \
the same games in the same order, each holding only "text": its description, written as the catalog's other \
descriptions are, in your own words from the facts. Where the facts say nothing real about a game, its "text" is null."""

ADULT = (" Each description is a frank, factual summary of an adult game's content, shown only to players who "
         "chose to see adult content.")


def conversation(adult, examples, batch):
    """The system and user messages asking for [batch] ({"<game key>": facts}) after [examples]."""
    request = {key: {"facts": entry["facts"]} for key, entry in examples.items()}
    request.update({key: {"facts": facts} for key, facts in batch.items()})
    return [{"role": "system", "content": BRIEF.format(adult=ADULT if adult else "")},
            {"role": "user", "content": json.dumps({"descriptions": request, "schemaVersion": 1},
                                                   ensure_ascii=False, indent=1)}]


def ask(model, adult, examples, batch):
    """The model's {"<game key>": description or None} for [batch], checked."""
    start = started(examples)
    text, finish = continued(model, conversation(adult, examples, batch), start)
    first = next(iter(batch))
    if finish != "stop":
        raise SystemExit(f"the batch from {first} ended with {finish}, not stop")
    try:
        answered = json.loads(start + text)
    except json.JSONDecodeError as error:
        raise SystemExit(f"the batch from {first} does not complete the started answer "
                         f"({error.msg} at {error.pos - len(start)} of the continuation)")
    if not isinstance(answered, dict) or not isinstance(answered.get("descriptions"), dict):
        raise SystemExit(f"the batch from {first} has no descriptions object")
    keys = set(answered["descriptions"])
    wanted = set(examples) | set(batch)
    if keys != wanted:
        raise SystemExit(f"the batch from {first} answers other games: missing "
                         f"{[key for key in batch if key not in keys][:5]}, "
                         f"{len(set(examples) - keys)} examples missing, extra {len(keys - wanted)}")
    written = {}
    for key in batch:
        entry = answered["descriptions"][key]
        if not isinstance(entry, dict) or set(entry) != {"text"}:
            raise SystemExit(f"{key}: the answer is not {{\"text\"}}")
        value = entry["text"]
        if value is not None and (not isinstance(value, str) or not 0 < len(value.strip()) <= descriptions.MAX_LENGTH):
            raise SystemExit(f"{key}: the description is not 1-{descriptions.MAX_LENGTH} characters or null")
        written[key] = value.strip() if value is not None else None
    return written


def pick_examples(done, facts, avoid, rng):
    """Up to EXAMPLES of the described games [done] ({"<game key>": description}) with their facts,
    none of the keys in [avoid]: {"<game key>": {"facts", "text"}}."""
    pool = sorted(key for key in done if key in facts and key not in avoid)
    if not pool:
        raise SystemExit("no described games to show as examples")
    return {key: {"facts": facts[key], "text": done[key]} for key in sorted(rng.sample(pool, min(EXAMPLES, len(pool))))}


def describe(root, adult, moby_root, references, model, batch, workers, limit):
    os.environ["FIREWORKS_API_KEY"]  # fail before any work without the key
    facts = descriptions.game_facts(root, moby_root, references)
    items = descriptions.worklist_items(root, moby_root, references, limit, adult)
    done = descriptions.read(root / f"catalog/research/descriptions{'-adult' if adult else ''}-v1.json.gz",
                             {"descriptions": {}})["descriptions"]
    print(f"{len(items)} games to describe{' frankly' if adult else ''} with {model.split('/')[-1]} "
          f"({len(done)} described)", flush=True)
    left = {item["key"]: facts[item["key"]] for item in items}
    rng = random.Random()
    work = [(part, pick_examples(done, facts, part, rng)) for part in batches(left, batch)]
    written = nothing = 0
    with ThreadPoolExecutor(workers) as pool:
        running = {pool.submit(ask, model, adult, examples, part) for part, examples in work}
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
                written += len(texts)
                nothing += len(result) - len(texts)
    print(f"{written} written, {nothing} with nothing real to say", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("root", type=Path)
    parser.add_argument("--adult", action="store_true")
    parser.add_argument("--mobygames", type=Path)
    parser.add_argument("--references", type=Path)
    parser.add_argument("--model", default=MODEL)
    parser.add_argument("--batch", type=int, default=BATCH)
    parser.add_argument("--workers", type=int, default=WORKERS)
    parser.add_argument("--limit", type=int)
    args = parser.parse_args()
    describe(args.root.resolve(), args.adult, args.mobygames, args.references, args.model, args.batch,
             args.workers, args.limit)


if __name__ == "__main__":
    main()
