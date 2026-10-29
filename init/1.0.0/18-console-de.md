---
title: German: add {{app}}'s Play listing in Play Console
kind: translation
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Implement: build it when it applies, as **Applies when** says.

## Applies when

The owner decides to release {{app}} on Google Play.

## Work

Depends on {{ref:listing-de}}.

- In Play Console, add German (`de-DE`) to {{app}}'s default store listing, with the short and full descriptions from `docs/play-assets/listing.de-DE.md`.
- Check Play's preview of the listing for cut text and broken line breaks.
- Send it for review with the release ({{ref:release}}).

## Done when

- Once reviewed, Google Play shows {{app}}'s listing in German on a device set to German.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- **Does not apply to {{system}}, or {{app}} already has it:** record what was checked and why in this description, then close as not planned.
