---
title: Run flat out while the guest loads from its media
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Implement, when it applies. Build it only if **Applies when** holds for {{system}}.

## Applies when

The machine loads from a drive, tape or CD.

## Work

- The core's `booting()` is true while the guest is loading from any medium: while a drive, tape or CD motor runs. The host then runs the machine as fast as it can, as in fast-forward.
- Not only during the first boot: loads later in a game run flat out too.

## Done when

- Loads at boot and later in play run flat out, and play returns to real speed after each.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
- **Does not apply, or {{app}} already has it:** write what was checked and why into this description, then close as not planned.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- Kairo64 reports the datasette motor and the 1541's loading (`C64Machine::loading`).
