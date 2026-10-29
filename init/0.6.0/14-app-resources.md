---
title: The app's resources: nothing duplicated, the archive wording right
kind: validation
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Validation. Check it; build nothing unless the check fails.

## Applies when

Every new system.

## Check

- No string every app has is defined in the app instead of coming from the shared frontend.
- No drawable is a copy of a shared one.
- The folder and empty-library texts name the ZIP and 7z archives the library reads.
- No text calls games or firmware "ROMs".

## Passes when

- None found.

## Closing

- **Passes:** write the build, the device and what was seen into this description, then close as completed.
- **Does not apply:** write why into this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one problem each), list them here, and close this one when they are closed and the check passes.
