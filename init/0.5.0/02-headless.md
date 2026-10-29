---
title: Headless video, sound and UI; the emulator's own menus never show
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Required: build it for every new system.

## Applies when

Every new system.

## Work

Replace the emulator's main loop, video, sound and UI so each frame and its sound go to the host. Its own menus and dialogs never show, not even after a guest crash.

## Done when

- No emulator UI appears in normal play, after a guest crash or at teardown.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- This is required for every new system; it is never closed as not planned.

## Learned in

`KairoDos#39`
