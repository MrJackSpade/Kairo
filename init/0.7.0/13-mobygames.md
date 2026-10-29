---
title: MobyGames in the catalog
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system, before its first release that is not a test build.

## Work

- Finish the MobyGames details and art passes (`mobygames.py`), then rebuild the research with `--mobygames`.
- Review the new matches. Games LaunchBox lacks become catalog games, and games LaunchBox has no cover for take MobyGames' cover and screenshot.

## Done when

- The catalog includes MobyGames.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
