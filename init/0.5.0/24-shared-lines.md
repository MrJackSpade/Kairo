---
title: Inputs that share hardware lines are never sent together
kind: validation
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Validation: check it. There is nothing to build unless the check fails.

## Applies when

Every new system.

## Check

Find inputs that share the machine's lines (the C64's joystick port 2 and keyboard matrix) and check that no default binding sends them together.

## Passes when

- Each binding reads in the guest as the one input it is.

## Closing

- **Passes:** record the build, the device and what was seen in this description, then close as completed.
- **Does not apply to {{system}}:** record why in this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one scope each), list them here, and close this once they are closed and the check passes.

## Learned in

`Kairo64#12`
