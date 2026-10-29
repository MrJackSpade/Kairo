---
title: The guest keyboard layout
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system.

## Work

`<Machine>Keys.kt`:
- the layout, from the shared blocks where they fit (the JIS main block, the number and symbol page);
- legends that follow the running model (international or JIS, kana where the machine has it);
- the mapping from Android key codes, the labels, the keys a controller can bind, and the modifiers.

## Done when

- Every key types what its legend shows, on each model.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- KairoMSX's keyboard showed international legends on Japanese machines (`KairoMSX#45`).
