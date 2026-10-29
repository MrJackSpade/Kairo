---
title: The library
kind: validation
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Validation. Check it; build nothing unless the check fails.

## Applies when

Every new system. The shared frontend is tested in other apps; this checks it again with {{app}}'s machine, keyboard, catalog and library.

## Check

- Scroll the full library by touch and with the D-pad held.
- Open and close the library menu by touch and by controller.

## Passes when

- Scrolling is smooth, the selection stays on screen, the menu follows focus, and no button keeps a stuck pressed look.

## Closing

- **Passes:** write the build, the device and what was seen into this description, then close as completed.
- **Does not apply:** write why into this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one problem each), list them here, and close this one when they are closed and the check passes.
