---
title: The first catalog, from LaunchBox
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system.

## Work

- Build the first catalog from LaunchBox alone, so device tests can start: the LaunchBox snapshot, `research.py` without `--mobygames`, `app_catalog.py`, `fetch_artwork.py`, then `art_review.py` (Kairo `docs/catalog-research.md`).
- Start the MobyGames dump beside it (the "MobyGames in the catalog" ticket); it is slow and must not hold up testing.

## Done when

- The app shows catalog titles and artwork for the test set.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
