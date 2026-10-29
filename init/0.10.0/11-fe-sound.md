---
title: Sound under input
kind: validation
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Validation. Check it; build nothing unless the check fails.

## Applies when

Every new system. The shared frontend is tested in other apps; this checks it again with {{app}}'s machine, keyboard, catalog and library.

## Check

- Play with constant touch, mouse and controller input.

## Passes when

- The sound never crackles or drops because of input.

## Closing

- **Passes:** write the build, the device and what was seen into this description, then close as completed.
- **Does not apply:** write why into this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one problem each), list them here, and close this one when they are closed and the check passes.
