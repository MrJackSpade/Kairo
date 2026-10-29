---
title: A frame's picture does not depend on how its drawing is split
kind: validation
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Validation. Check it; build nothing unless the check fails.

## Applies when

The core draws a frame in pieces (part of it before each mid-frame change, the rest at the end).

## Check

- Run the same frames with and without the mid-frame splits in `game_profile` and compare the frame hashes.

## Passes when

- The pictures are the same.

## Closing

- **Passes:** write the build, the device and what was seen into this description, then close as completed.
- **Does not apply:** write why into this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one problem each), list them here, and close this one when they are closed and the check passes.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- Kairo68: a sprite pixel in Arkanoid: Revenge of Doh changed with the split (`Kairo68#33`).
