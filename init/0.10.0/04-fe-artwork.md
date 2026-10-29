---
title: Artwork
kind: validation
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Validation: check it. There is nothing to build unless the check fails.

## Applies when

Every new system: the shared frontend's fixed issues, checked again with this app's machine, keyboard, catalog and library.

## Check

Scroll fast on the bottom screen; Download missing images on the full library; set and reset a game's own art.

## Passes when

- No pop-in or flashing, progress at once, no failures, rate limits waited out, the game's art set and reset.

## Closing

- **Passes:** record the build, the device and what was seen in this description, then close as completed.
- **Does not apply to {{system}}:** record why in this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one scope each), list them here, and close this once they are closed and the check passes.

## Learned in

`Kairo#8`, `Kairo#33`, `Kairo#35`, `KairoDos#83`, `KairoDos#16`
