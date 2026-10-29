---
title: Full speed on the heaviest games
kind: validation
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Validation. Check it; build nothing unless the check fails.

## Applies when

Every new system. Test on the RG DS with a release-like build (`shared\tools\build.ps1 -Install`).

## Check

- Play the heaviest action games for several minutes (not still screens) and read the frame host's reports.
- Measure how much faster than real time the machine runs flat out during a load.
- A still screen should cost little. Crackling sound is audio underruns: find the work in the frame that causes it.

## Passes when

- The guest's frame rate with no audio underruns. Anything below full speed goes to the performance tickets.

## Closing

- **Passes:** write the build, the device and what was seen into this description, then close as completed.
- **Does not apply:** write why into this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one problem each), list them here, and close this one when they are closed and the check passes.
