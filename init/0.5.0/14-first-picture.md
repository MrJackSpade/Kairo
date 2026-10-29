---
title: Play reaches the game's first picture quickly
kind: validation
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Validation. Check it; build nothing unless the check fails.

## Applies when

Every new system.

## Check

- Measure on the RG DS from tapping Play to the game's first picture, split into: extracting the game, preparing the firmware, starting the emulator, and the guest's own boot.
- The boot runs flat out; a splash the shipped firmware holds on screen is shortened or skipped.

## Passes when

- No black or splash screen holds for seconds before the game draws.

## Closing

- **Passes:** write the build, the device and what was seen into this description, then close as completed.
- **Does not apply:** write why into this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one problem each), list them here, and close this one when they are closed and the check passes.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- KairoST: EmuTOS's welcome screen held for over 10 seconds (`KairoST#5`); Kairo68: the X68000's memory check (`Kairo68#2`).
