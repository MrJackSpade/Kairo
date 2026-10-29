---
title: A save state loads back exactly
kind: validation
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Validation. Check it; build nothing unless the check fails.

## Applies when

The app has save states.

## Check

- In `boot_test`, save a state during play and during a load, run on, load it, and compare the machine one frame after the load with one frame after the save, every chip included.

## Passes when

- No difference, on several games.

## Closing

- **Passes:** write the build, the device and what was seen into this description, then close as completed.
- **Does not apply:** write why into this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one problem each), list them here, and close this one when they are closed and the check passes.
