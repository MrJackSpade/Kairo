---
title: The emulated mouse is Kairo's mouse
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Implement: build it when it applies, as **Applies when** says.

## Applies when

The machine has a mouse.

## Work

Map the emulated mouse's axes and buttons to Kairo's mouse; the core fails when they are not mapped.

## Done when

- The touchpad and the right stick move the pointer in a mouse game.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- **Does not apply to {{system}}, or {{app}} already has it:** record what was checked and why in this description, then close as not planned.

## Learned in

`Kairo68#32`, `Kairo98#66`
