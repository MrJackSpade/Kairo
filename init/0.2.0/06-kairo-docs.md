---
title: List {{app}} in Kairo's documentation
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system. The work is in the Kairo repository (the shared frontend); this ticket tracks it for {{app}}.

## Work

- The platforms table in `docs/catalog-research.md` (slug, app, LaunchBox platform, MobyGames ID, artwork code).
- {{app}}'s own UI tests in `docs/ui-tests.md`, once they exist.
- The README's family grid, regenerated with `tools/docs.py`.

## Done when

- All three are on Kairo's `main`.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
