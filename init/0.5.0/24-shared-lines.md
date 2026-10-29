---
title: No binding sends inputs the machine can't tell apart
kind: validation
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Validation. Check it; build nothing unless the check fails.

## Applies when

Every new system.

## Check

- Find inputs that share hardware lines on {{system}} (a joystick port wired into the keyboard matrix, for example).
- Check that no default binding sends two of them together.

## Passes when

- Each binding reads in the guest as the one input it is meant to be.

## Closing

- **Passes:** write the build, the device and what was seen into this description, then close as completed.
- **Does not apply:** write why into this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one problem each), list them here, and close this one when they are closed and the check passes.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- Kairo64: the C64's joystick port 2 shares lines with the keyboard matrix, so a D-pad sending both the joystick and Shift+cursor read as up and down at once (`Kairo64#12`).
