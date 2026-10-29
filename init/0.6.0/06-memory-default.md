---
title: Memory defaults as high as games allow
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Implement, when it applies. Build it only if **Applies when** holds for {{system}}.

## Applies when

The machine came with more than one memory size.

## Work

- A memory machine option defaulting to the most memory that breaks no game, with a per-game override for the games that need less.

## Done when

- Games start without running out of memory, and the ones that need less have it in the catalog.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
- **Does not apply, or {{app}} already has it:** write what was checked and why into this description, then close as not planned.
