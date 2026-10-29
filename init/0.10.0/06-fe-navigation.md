---
title: Navigation
kind: validation
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Validation. Check it; build nothing unless the check fails.

## Applies when

Every new system. The shared frontend is tested in other apps; this checks it again with {{app}}'s machine, keyboard, catalog and library.

## Check

- Back in the library and in a game; Library during a game.
- Every dialog by D-pad, and in portrait.
- Back from the on-screen controls editor; the edge gestures.

## Passes when

- Back closes the topmost layer and opens the menu in a game; the library opens over a paused game and Back resumes it; leaving for the library asks first; dialogs work by D-pad and show above the menu; Back from the editor returns to the controller page; the gestures open the menu and the keyboard.

## Closing

- **Passes:** write the build, the device and what was seen into this description, then close as completed.
- **Does not apply:** write why into this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one problem each), list them here, and close this one when they are closed and the check passes.
