---
title: Spanish: translate {{app}}'s catalog descriptions
kind: translation
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Required: proper multilingual support is a hard requirement for every new system.

## Applies when

Every new system. Proper multilingual support is a hard requirement.

## Work

Depends on {{ref:descriptions}}, and on {{ref:adult-descriptions}} where the catalog has adult games.

- Work in batches with `shared/tools/descriptions.py`: `pending <app root> es <out.json>` gives the English descriptions not yet translated, or translated from English that has since changed; translate them; merge with `translate <app root> es <batch.json>`. Do the same with `--adult` for the adult descriptions.
- Write natural Spanish that says what the English says, not a word-for-word copy.
- Names (games, series, companies, people, characters, places) keep their Latin form unless Spanish has an established form of that very thing: an official localized title or a standard exonym, confirmed with `descriptions.py names` and `name_forms.py`. Never translate a title on the spot.
- Translate the adult descriptions fully and factually, including what they say about minors. A description is never left out, softened or replaced by a note because of its content.
- Rebuild with `research.py` and `app_catalog.py`.
- One neutral Spanish that reads naturally in Latin America and in Spain, with no regional slang.

## Done when

- `descriptions.py stale <app root>` reports no stale Spanish translation, main or adult, and `pending` returns nothing for es, with or without `--adult`.
- `audit_core_catalog.py` passes.
- A device set to Spanish shows the translations, checked on several games, an adult one among them.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- This is required for every new system; it is never closed as not planned.
