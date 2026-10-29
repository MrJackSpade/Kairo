---
title: Run the core headless, with none of its own UI
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system.

## Work

- Replace the emulator's main loop, video, sound and UI output so each frame and its sound go to the Kairo host.
- The emulator's own menus, dialogs and on-screen messages never show, not even after the guest crashes or the emulator hits an error.

## Done when

- In normal play, after a guest crash and at the end of a game, only Kairo's UI shows.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
