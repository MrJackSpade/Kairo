---
title: Controller
kind: validation
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Validation: check it. There is nothing to build unless the check fails.

## Applies when

Every new system: the shared frontend's fixed issues, checked again with this app's machine, keyboard, catalog and library.

## Check

Hold inputs through focus loss, pause, an app switch, the screen locking and a disconnect; first run; the controller menu in a game with its own mapping; edit a chosen key.

## Passes when

- Nothing stays held; the deadzone defaults to 10%; first run offers with and without sticks; the menu shows the mapping in use; Add or Replace is offered; the mapping icon never flashes.

## Closing

- **Passes:** record the build, the device and what was seen in this description, then close as completed.
- **Does not apply to {{system}}:** record why in this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one scope each), list them here, and close this once they are closed and the check passes.

## Learned in

`KairoDos#4`, `KairoDos#5`, `Kairo#7`, `KairoDos#80`, `Kairo#34`, `KairoDos#84`, `Kairo#5`, `Kairo#31`
