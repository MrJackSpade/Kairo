---
title: Every media kind plays past its load
kind: validation
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Validation: check it. There is nothing to build unless the check fails.

## Applies when

Every new system, on the RG DS, each finding its own ticket.

## Check

Start one game of every media kind, loose and in a ZIP and a 7z; play past its load, use the controller, save and load a state, then start a second game in the same process.

## Passes when

- Each does all of that.

## Closing

- **Passes:** record the build, the device and what was seen in this description, then close as completed.
- **Does not apply to {{system}}:** record why in this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one scope each), list them here, and close this once they are closed and the check passes.

## Learned in

`Kairo64#8`
