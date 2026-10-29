---
title: The controller
kind: validation
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Validation. Check it; build nothing unless the check fails.

## Applies when

Every new system. The shared frontend is tested in other apps; this checks it again with {{app}}'s machine, keyboard, catalog and library.

## Check

- Hold inputs through losing focus, pausing, switching apps, locking the screen and disconnecting the controller.
- First run with a controller.
- The controller menu in a game with its own mapping.
- Choose a key for a button that has one.

## Passes when

- No input stays held; the deadzone is 10%; first run offers with and without sticks; the menu shows the mapping the game uses; Add or Replace is offered; the mapping icon doesn't flash on button presses.

## Closing

- **Passes:** write the build, the device and what was seen into this description, then close as completed.
- **Does not apply:** write why into this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one problem each), list them here, and close this one when they are closed and the check passes.
