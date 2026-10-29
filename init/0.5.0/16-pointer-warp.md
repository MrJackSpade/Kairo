---
title: Pointer warp for direct touch
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Implement: build it when it applies, as **Applies when** says.

## Applies when

The machine has a mouse or pointer.

## Work

The core moves the guest pointer to a frame point, so a tap puts it where the player touched.

## Done when

- Direct touch puts the pointer under the finger in a mouse game.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- **Does not apply to {{system}}, or {{app}} already has it:** record what was checked and why in this description, then close as not planned.

## Learned in

`Kairo#79`
