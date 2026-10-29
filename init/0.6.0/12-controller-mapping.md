---
title: The default controller mapping
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system.

## Work

The mapping lives in `catalog/system-v1.json`, never in Kotlin defaults, so it ships with the catalog:
- every input mapped, in both controller layouts (with and without sticks); a duplicate is better than an unmapped input;
- the D-pad sends the arrow keys and the joystick (unless `{{ref:shared-lines}}` forbids it);
- A is Return, B is Space, X and Y the joystick's buttons;
- the left stick the joystick, the right stick the mouse where there is one.

## Done when

- No input is left unmapped in either layout, and each reaches the game as described.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- Kairo64's `catalog/system-v1.json`.
