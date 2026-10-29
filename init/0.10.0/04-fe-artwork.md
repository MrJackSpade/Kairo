---
title: Artwork
kind: validation
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Validation. Check it; build nothing unless the check fails.

## Applies when

Every new system. The shared frontend is tested in other apps; this checks it again with {{app}}'s machine, keyboard, catalog and library.

## Check

- Scroll fast with the bottom screen showing details.
- Run Download missing images on the full library.
- Set and reset a game's own box art and screenshot.

## Passes when

- No artwork pops in late or flashes; the download shows progress at once, waits out rate limits and ends with no failures; a game's own art is set and reset.

## Closing

- **Passes:** write the build, the device and what was seen into this description, then close as completed.
- **Does not apply:** write why into this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one problem each), list them here, and close this one when they are closed and the check passes.
