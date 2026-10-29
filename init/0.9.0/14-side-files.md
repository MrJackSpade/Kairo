---
title: A file a patched game needs loads for that game
kind: validation
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Validation. Check it; build nothing unless the check fails.

## Applies when

Patched or translated games need a file beside them (a font, a patch file).

## Check

- Start such a game, then another game.

## Passes when

- The file loads for its own game only.

## Closing

- **Passes:** write the build, the device and what was seen into this description, then close as completed.
- **Does not apply:** write why into this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one problem each), list them here, and close this one when they are closed and the check passes.
