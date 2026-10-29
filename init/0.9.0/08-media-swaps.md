---
title: Media swaps and save states
kind: validation
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Validation. Check it; build nothing unless the check fails.

## Applies when

The machine has media that change during play.

## Check

- Swap a disk in the middle of a game.
- Save after the swap, then load that state in a session started on the first disk.
- Look at the Disks page after the load.

## Passes when

- The game keeps running, the state continues on its own disk, and the Disks page shows the state's disks.

## Closing

- **Passes:** write the build, the device and what was seen into this description, then close as completed.
- **Does not apply:** write why into this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one problem each), list them here, and close this one when they are closed and the check passes.
