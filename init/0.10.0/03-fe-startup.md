---
title: Startup
kind: validation
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Validation: check it. There is nothing to build unless the check fails.

## Applies when

Every new system: the shared frontend's fixed issues, checked again with this app's machine, keyboard, catalog and library.

## Check

Cold and warm starts with a catalog check.

## Passes when

- The check shows progress without blocking, and the library comes up as fast as the other apps'.

## Closing

- **Passes:** record the build, the device and what was seen in this description, then close as completed.
- **Does not apply to {{system}}:** record why in this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one scope each), list them here, and close this once they are closed and the check passes.

## Learned in

`Kairo#14`, `Kairo#15`
