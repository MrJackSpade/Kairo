---
title: No duplicated text or drawables; the archive wording
kind: validation
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Validation: check it. There is nothing to build unless the check fails.

## Applies when

Every new system.

## Check

Check the app's resources against the shared frontend's: no string every app has, no shared drawable copied, the folder and empty-library texts naming ZIP and 7z archives; games never called ROMs.

## Passes when

- None found.

## Closing

- **Passes:** record the build, the device and what was seen in this description, then close as completed.
- **Does not apply to {{system}}:** record why in this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one scope each), list them here, and close this once they are closed and the check passes.

## Learned in

`Kairo#95`, `Kairo#97`, `Kairo98#109`
