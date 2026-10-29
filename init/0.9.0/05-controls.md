---
title: Every control reaches the guest as itself
kind: validation
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Validation: check it. There is nothing to build unless the check fails.

## Applies when

Every new system, on the RG DS, each finding its own ticket.

## Check

Read each D-pad direction and stick alone in the guest (a BASIC `PEEK` loop or the like); the cursor keys from the D-pad, the on-screen keyboard and a physical keyboard at the guest's prompt; every button in both controller layouts; the bottom screen's Joystick tab.

## Passes when

- Each reads as itself, and the Joystick tab shows the joystick bindings.

## Closing

- **Passes:** record the build, the device and what was seen in this description, then close as completed.
- **Does not apply to {{system}}:** record why in this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one scope each), list them here, and close this once they are closed and the check passes.

## Learned in

`Kairo64#12`, `Kairo64#15`, `Kairo#38`
