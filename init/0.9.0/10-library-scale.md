---
title: The library at full size
kind: validation
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Validation. Check it; build nothing unless the check fails.

## Applies when

Every new system. Test on the RG DS with a release-like build (`shared\tools\build.ps1 -Install`).

## Check

With the full standard sets on the SD card:
- start the app twice, and again after updating it;
- search while typing;
- open the folder pickers after choosing the games folder.

## Passes when

- A second start hashes nothing it already knows and finishes its check quickly, an update rehashes nothing, archives are read in place without copies, search responds as you type, and the pickers show the right folders.

## Closing

- **Passes:** write the build, the device and what was seen into this description, then close as completed.
- **Does not apply:** write why into this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one problem each), list them here, and close this one when they are closed and the check passes.
