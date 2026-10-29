---
title: Connect the emulated mouse to Kairo's mouse
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Implement, when it applies. Build it only if **Applies when** holds for {{system}}.

## Applies when

The machine has a mouse.

## Work

- Map the emulated mouse's axes and buttons to the mouse the host drives. The core fails at start if they are not mapped, so a missing mapping can't pass unnoticed.

## Done when

- The touchpad and the right stick move the pointer in a mouse game.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
- **Does not apply, or {{app}} already has it:** write what was checked and why into this description, then close as not planned.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- Kairo68: MAME mapped the X68000's mouse to a host mouse Kairo never had (`Kairo68#32`).
