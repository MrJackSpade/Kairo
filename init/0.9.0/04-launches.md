---
title: Starting games: first picture, several in a row, other frontends
kind: validation
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Validation. Check it; build nothing unless the check fails.

## Applies when

Every new system. Test on the RG DS with a release-like build (`shared\tools\build.ps1 -Install`).

## Check

- Time from Play to the first picture.
- Several games in one process: a program after a disk, a game after a loaded state.
- Launches from ES-DE, NeoStation and Daijisho.

## Passes when

- The first picture comes quickly, every game runs as it does alone, and a frontend's launch is the library's game, with its saves and settings.

## Closing

- **Passes:** write the build, the device and what was seen into this description, then close as completed.
- **Does not apply:** write why into this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one problem each), list them here, and close this one when they are closed and the check passes.
