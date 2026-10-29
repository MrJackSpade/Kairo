---
title: Screen
kind: validation
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Validation: check it. There is nothing to build unless the check fails.

## Applies when

Every new system: the shared frontend's fixed issues, checked again with this app's machine, keyboard, catalog and library.

## Check

Play, idle in the library, rotate, set scaling, crop, notch padding and the anchor; toggle sound.

## Passes when

- The game is full screen with no status bar; the library lets the device sleep and a game keeps it awake; scaling and crop are kept per orientation; details stack in portrait; sound shows On or Muted.

## Closing

- **Passes:** record the build, the device and what was seen in this description, then close as completed.
- **Does not apply to {{system}}:** record why in this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one scope each), list them here, and close this once they are closed and the check passes.

## Learned in

`KairoDos#10`, `KairoDos#38`, `Kairo#90`, `Kairo98#45`, `Kairo98#46`, `Kairo#54`, `Kairo98#47`, `Kairo#19`
