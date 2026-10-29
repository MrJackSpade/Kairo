---
title: Every control reaches the guest as itself
kind: validation
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Validation. Check it; build nothing unless the check fails.

## Applies when

Every new system. Test on the RG DS with a release-like build (`shared\tools\build.ps1 -Install`).

## Check

- Read each D-pad direction and each stick direction alone in the guest (a BASIC loop reading the joystick, or a game that shows it).
- Press the cursor keys from the D-pad, the on-screen keyboard and a physical keyboard at the guest's prompt.
- Press every button in both controller layouts.
- Open the bottom screen's Joystick tab.

## Passes when

- Each reads as itself, and the Joystick tab shows the joystick bindings.

## Closing

- **Passes:** write the build, the device and what was seen into this description, then close as completed.
- **Does not apply:** write why into this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one problem each), list them here, and close this one when they are closed and the check passes.
