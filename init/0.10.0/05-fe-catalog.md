---
title: Catalog updates
kind: validation
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Validation: check it. There is nothing to build unless the check fails.

## Applies when

Every new system: the shared frontend's fixed issues, checked again with this app's machine, keyboard, catalog and library.

## Check

Activate a downloaded catalog, update the app; apply a partial update.

## Passes when

- The downloaded catalog stays active, a partial update changes only its fields, the update stays within its size limit. Whether an update can change controller mappings is recorded here (`Kairo#55`).

## Closing

- **Passes:** record the build, the device and what was seen in this description, then close as completed.
- **Does not apply to {{system}}:** record why in this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one scope each), list them here, and close this once they are closed and the check passes.

## Learned in

`Kairo#26`, `KairoDos#7`, `Kairo#75`, `Kairo#55`
