---
title: Mark the adult artwork
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Implement, when it applies. Build it only if **Applies when** holds for {{system}}.

## Applies when

The owner names images as adult.

## Work

- List them under `adultArtwork` in `catalog/core-review-v1.json`, so they ship only in the adult artwork part.

## Done when

- The main artwork part holds none of them.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
- **Does not apply, or {{app}} already has it:** write what was checked and why into this description, then close as not planned.
