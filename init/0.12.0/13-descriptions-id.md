---
title: Indonesian: translate {{app}}'s catalog descriptions
kind: translation
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system. Proper multilingual support is a hard requirement.

## Work

Depends on {{ref:descriptions}}, and on {{ref:adult-descriptions}} where the catalog has adult games.

- Work in batches with `shared/tools/descriptions.py`: `pending <app root> id <out.json>` gives the English descriptions not yet translated or translated from English that has since changed; translate them; merge them with `translate <app root> id <batch.json>`. Do the same with `--adult` for the adult descriptions.
- Write natural Indonesian that says what the English says, not a word-for-word copy.
- Names (games, series, companies, people, characters, places) keep their Latin form unless Indonesian has an established form of that very thing: an official localized title or a standard exonym, confirmed with `descriptions.py names` and `shared/tools/name_forms.py`. Never translate a title on the spot.
- Translate the adult descriptions fully and factually, including what they say about minors. A description is never left out, softened or replaced by a note because of its content.
- Rebuild with `research.py` and `app_catalog.py`.

## Done when

- `descriptions.py stale <app root>` reports no stale Indonesian translation, main or adult, and `pending` returns nothing for `id`, with or without `--adult`.
- `audit_core_catalog.py` passes.
- A device set to Indonesian shows the translations, checked on several games, an adult one among them.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
