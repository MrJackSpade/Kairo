---
title: A save state loads back exactly
kind: validation
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Validation. Check it; build nothing unless the check fails.

## Applies when

The app has save states.

## Check

- The shared PC boot test's `state` check (Kairo#115), in {{app}}'s `core/test/boot-tests.json` with `"sound": true`: save during a load, run on, load, and run the same frames again; every frame's picture and sound after the load must be the same frame's after the save, the first one too (the paused game under the menu shows it).

## Passes when

- No difference, on several games.

## Closing

- **Passes:** write the build, the device and what was seen into this description, then close as completed.
- **Does not apply:** write why into this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one problem each), list them here, and close this one when they are closed and the check passes.
