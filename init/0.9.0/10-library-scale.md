---
title: The library at full size
kind: validation
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Validation: check it. There is nothing to build unless the check fails.

## Applies when

Every new system, on the RG DS, each finding its own ticket.

## Check

The full standard set on the SD card: a second start, an app update, search, the folder pickers after a large games folder.

## Passes when

- A second start hashes nothing new and checks quickly, an update rehashes nothing, archives are read in place, search responds while typing, no catalog parse on the UI thread, the pickers work.

## Closing

- **Passes:** record the build, the device and what was seen in this description, then close as completed.
- **Does not apply to {{system}}:** record why in this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one scope each), list them here, and close this once they are closed and the check passes.

## Learned in

`Kairo#46`, `Kairo#39`, `Kairo#52`, `Kairo#51`, `Kairo#9`, `Kairo#32`
