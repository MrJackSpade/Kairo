---
title: The core's per-game options reach the machine
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Implement, when it applies. Build it only if **Applies when** holds for {{system}}.

## Applies when

The core keeps settings or patches for particular games.

## Work

- Give them a catalog setting, which is also a per-game setting the player can change.
- Pass it to the core at start.
- Record in the catalog the games that need one.

## Done when

- A game that needs one runs correctly with it.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
- **Does not apply, or {{app}} already has it:** write what was checked and why into this description, then close as not planned.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- KairoTowns: Tsugaru's application-specific settings (`-APP`) for games such as Ultima Underworld (`KairoTowns#77`).
