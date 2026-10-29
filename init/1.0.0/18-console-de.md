---
title: German: add {{app}}'s Play listing in Play Console
kind: translation
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Implement, when it applies. Build it only if **Applies when** holds for {{system}}.

## Applies when

The owner decides to release {{app}} on Google Play.

## Work

Depends on {{ref:listing-de}}.

- In Play Console, add German (`de-DE`) to {{app}}'s default store listing, with the short and full descriptions from `docs/play-assets/listing.de-DE.md`.
- Check Play's preview for cut text and broken line breaks.
- Send it for review with the release ({{ref:release}}).

## Done when

- Once reviewed, Google Play shows {{app}}'s listing in German on a device set to German.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
- **Does not apply, or {{app}} already has it:** write what was checked and why into this description, then close as not planned.
