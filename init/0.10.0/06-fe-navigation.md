---
title: Navigation
kind: validation
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Validation: check it. There is nothing to build unless the check fails.

## Applies when

Every new system: the shared frontend's fixed issues, checked again with this app's machine, keyboard, catalog and library.

## Check

Back in the library and in a game; Library during a game; every dialog by D-pad and in portrait; Back from the on-screen controls; edge gestures.

## Passes when

- Back closes the topmost layer and opens the flyout in a game; the library opens over a paused game and Back resumes it; Library ends it after asking; dialogs work with the D-pad and show above the flyout; Back returns to the controller page; the gestures work.

## Closing

- **Passes:** record the build, the device and what was seen in this description, then close as completed.
- **Does not apply to {{system}}:** record why in this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one scope each), list them here, and close this once they are closed and the check passes.

## Learned in

`KairoDos#2`, `Kairo98#35`, `KairoDos#3`, `Kairo#13`, `Kairo#12`, `Kairo98#59`, `Kairo#4`, `KairoDos#9`
