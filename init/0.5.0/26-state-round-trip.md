---
title: A save state round trip is exact
kind: validation
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Validation: check it. There is nothing to build unless the check fails.

## Applies when

The app has save states.

## Check

In `boot_test`, save a state during play and during a load, run on, load it, and compare the machine a frame after the load with a frame after the save, every chip included.

## Passes when

- No drift, on several games.

## Closing

- **Passes:** record the build, the device and what was seen in this description, then close as completed.
- **Does not apply to {{system}}:** record why in this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one scope each), list them here, and close this once they are closed and the check passes.

## Learned in

`Kairo68#72`
