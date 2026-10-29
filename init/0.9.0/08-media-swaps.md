---
title: Media swaps and states
kind: validation
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Validation: check it. There is nothing to build unless the check fails.

## Applies when

The machine has changeable media.

## Check

Swap a disk mid-game; save after the swap, load from a session on the first disk; check the Disks page after the load.

## Passes when

- The game keeps running, the state loads onto its own disk, the Disks page shows the state's disks.

## Closing

- **Passes:** record the build, the device and what was seen in this description, then close as completed.
- **Does not apply to {{system}}:** record why in this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one scope each), list them here, and close this once they are closed and the check passes.

## Learned in

`Kairo#47`, `Kairo68#69`
