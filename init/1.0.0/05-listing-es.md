---
title: Spanish: translate {{app}}'s Play listing
kind: translation
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Implement, when it applies. Build it only if **Applies when** holds for {{system}}.

## Applies when

The owner decides to release {{app}} on Google Play.

## Work

Depends on {{ref:play-listing}}.

- Write the short and full descriptions in Spanish from `docs/play-assets/listing.md`, as a store listing written in Spanish would read: the same facts and features, and no claim the English doesn't make.
- The short description stays within 80 characters and the full one within 4,000. Count them; Spanish often runs longer than English.
- The app name stays {{app}}. The machine's name takes the form Spanish uses for it where one is established (`name_forms.py`).
- No adult wording: the listing is for every audience, as the English is.
- Save it as `docs/play-assets/listing.es-419.md`, laid out like `listing.md`.
- One neutral Spanish that reads naturally in Latin America and in Spain, with no regional slang.

## Done when

- `docs/play-assets/listing.es-419.md` is committed, within both limits, and reads as Spanish written for the store.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
- **Does not apply, or {{app}} already has it:** write what was checked and why into this description, then close as not planned.
