---
title: The firmware chooser
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Implement, when it applies. Build it only if **Applies when** holds for {{system}}.

## Applies when

Choosing the player's firmware needs more than matching files by name (a version told by its header, one firmware per model).

## Work

- A helper that identifies the player's firmware and picks the right one for each game's model.

## Done when

- Each game runs on the firmware its model needs.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
- **Does not apply, or {{app}} already has it:** write what was checked and why into this description, then close as not planned.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- KairoST's `StTos.kt`; Kairo64's `C64Firmware.kt`.
