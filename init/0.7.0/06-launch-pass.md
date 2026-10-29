---
title: Launch pass: every game booted on the host core
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Required: build it for every new system.

## Applies when

Every new system.

## Work

Boot every library game on the host core on each setup until one runs it. The pass presses Start, Space and fire, plays past the title, and judges by the picture, not by sound. It records the firmware, drive, model and start program each game needs, and the dumps nothing starts (`BROKEN_DISK`), which never lead their set.

## Done when

- The catalog gives every game the setup it runs on, and the games nothing starts are listed here.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- This is required for every new system; it is never closed as not planned.

## Learned in

`KairoST#6`, `KairoST#35`, `KairoST#38`, `Kairo64#6`
