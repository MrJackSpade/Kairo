---
title: Controller mapping in catalog/system-v1.json
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Required: build it for every new system.

## Applies when

Every new system.

## Work

The mapping lives in `catalog/system-v1.json`, never in Kotlin defaults. Every input mapped in both controller layouts, with and without sticks; the D-pad the arrow keys and the joystick (`{{ref:shared-lines}}` permitting); A Return and B Space, X and Y the joystick's buttons; the right stick the mouse.

## Done when

- No input is left unmapped in either layout.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- This is required for every new system; it is never closed as not planned.

## Learned in

`Kairo#37`, `KairoMSX#4`, `KairoMSX#5`, `KairoTowns#4`, `Kairo64#15`
