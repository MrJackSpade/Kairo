---
title: AGENTS.md, README, licenses and privacy policy
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system.

## Work

- `AGENTS.md`: the rules for working in this repository. The core is credited accurately but never used as the app's brand; no proprietary firmware or games are committed or shipped; the GitHub and Google Play builds have the same features; every setting the catalog gives a game is also a per-game setting the player can change.
- `README.md`, with the shared `kairo:` blocks.
- `COPYING`, and `LICENSE.md` and the privacy policy written by `shared/tools/docs.py`.

## Done when

- `python shared/tools/docs.py . --check` passes.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- KairoST's and Kairo64's `AGENTS.md`.
