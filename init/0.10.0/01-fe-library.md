---
title: Library browsing
kind: validation
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Validation: check it. There is nothing to build unless the check fails.

## Applies when

Every new system: the shared frontend's fixed issues, checked again with this app's machine, keyboard, catalog and library.

## Check

Scroll the full library by touch and held D-pad; open and close the library menu.

## Passes when

- Scrolling is smooth, the selection stays on screen, the menu scrolls with focus, the Menu button keeps no stuck state.

## Closing

- **Passes:** record the build, the device and what was seen in this description, then close as completed.
- **Does not apply to {{system}}:** record why in this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one scope each), list them here, and close this once they are closed and the check passes.

## Learned in

`Kairo#1`, `Kairo#2`, `Kairo#6`, `Kairo#20`
