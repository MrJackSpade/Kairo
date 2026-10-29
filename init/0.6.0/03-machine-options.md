---
title: Every machine setting is a machine option with a per-game override
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system.

## Work

- Each machine setting is a `MachineOption`: an all-games default on the Machine page and a per-game override in each game's settings, kept as the player's catalog override. Whatever the catalog sets for a game, the player can change.

## Done when

- Every setting shows on the Machine page and in each game's settings, and the shared `CatalogSettingsTest` passes.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
