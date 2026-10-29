---
title: Settings pages
kind: validation
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Validation: check it. There is nothing to build unless the check fails.

## Applies when

Every new system: the shared frontend's fixed issues, checked again with this app's machine, keyboard, catalog and library.

## Check

Move through game settings by D-pad; edit a text field; open the status row.

## Passes when

- The selection stays on screen both ways, choice rows never move their label, text fields allow the full-screen keyboard, the status row opens details.

## Closing

- **Passes:** record the build, the device and what was seen in this description, then close as completed.
- **Does not apply to {{system}}:** record why in this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one scope each), list them here, and close this once they are closed and the check passes.

## Learned in

`Kairo#30`, `Kairo#28`, `Kairo#29`, `KairoDos#15`
