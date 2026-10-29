---
title: A game whose files the player changed still starts
kind: validation
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Validation. Check it; build nothing unless the check fails.

## Applies when

Every new system. Test on the RG DS with a release-like build (`shared\tools\build.ps1 -Install`).

## Check

- Add a file to a game (a configuration file or batch file), then start it.

## Passes when

- It starts and keeps its saves and settings.

## Closing

- **Passes:** write the build, the device and what was seen into this description, then close as completed.
- **Does not apply:** write why into this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one problem each), list them here, and close this one when they are closed and the check passes.
