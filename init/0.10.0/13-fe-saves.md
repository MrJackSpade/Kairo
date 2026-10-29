---
title: Saves
kind: validation
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Validation: check it. There is nothing to build unless the check fails.

## Applies when

Every new system: the shared frontend's fixed issues, checked again with this app's machine, keyboard, catalog and library.

## Check

Save and load slots; choose a save folder; Reset defaults on a game.

## Passes when

- Slots have thumbnails and empty loads are disabled; saves are named by the game's path in app storage and the chosen folder; Reset clears every per-game setting and nothing else.

## Closing

- **Passes:** record the build, the device and what was seen in this description, then close as completed.
- **Does not apply to {{system}}:** record why in this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one scope each), list them here, and close this once they are closed and the check passes.

## Learned in

`KairoDos#13`, `Kairo#57`, `Kairo98#56`, `KairoDos#6`
