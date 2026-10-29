---
title: The catalog's name index matches the app's file name lookup
kind: validation
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Validation. Check it; build nothing unless the check fails.

## Applies when

Every new system.

## Check

- Check that the catalog generator indexes names the same way the app normalizes a file name before looking it up: titles with a dot near the end, side and disk tags, the app's media extensions.

## Passes when

- Every test-set file name finds its game.

## Closing

- **Passes:** write the build, the device and what was seen into this description, then close as completed.
- **Does not apply:** write why into this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one problem each), list them here, and close this one when they are closed and the check passes.
