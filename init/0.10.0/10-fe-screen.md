---
title: The screen
kind: validation
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Validation. Check it; build nothing unless the check fails.

## Applies when

Every new system. The shared frontend is tested in other apps; this checks it again with {{app}}'s machine, keyboard, catalog and library.

## Check

- Play, then leave the library idle.
- Rotate; set scaling, crop, notch padding and the anchor in each orientation.
- Toggle the sound.

## Passes when

- The game is full screen without the status bar; the library lets the device sleep and a game keeps it awake; scaling and crop are kept per orientation; details stack in portrait; the sound shows On or Muted.

## Closing

- **Passes:** write the build, the device and what was seen into this description, then close as completed.
- **Does not apply:** write why into this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one problem each), list them here, and close this one when they are closed and the check passes.
