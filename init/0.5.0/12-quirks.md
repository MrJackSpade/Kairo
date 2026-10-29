---
title: The core's display artefacts are chosen on purpose
kind: validation
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Validation. Check it; build nothing unless the check fails.

## Applies when

The core emulates artefacts of the original display (monitor wires, scanline effects, colour bleed).

## Check

- List them and ask the owner, for each, whether it stays or is switched off.

## Passes when

- Each is listed here with the owner's decision, and the core does as decided.

## Closing

- **Passes:** write the build, the device and what was seen into this description, then close as completed.
- **Does not apply:** write why into this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one problem each), list them here, and close this one when they are closed and the check passes.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- KairoTowns: Tsugaru draws a Trinitron damper wire line across the picture (`KairoTowns#5`).
