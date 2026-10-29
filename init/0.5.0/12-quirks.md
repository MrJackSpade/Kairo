---
title: Emulated display quirks are decided on purpose
kind: validation
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Validation: check it. There is nothing to build unless the check fails.

## Applies when

The core emulates display artefacts of the original hardware (Tsugaru's Trinitron damper line).

## Check

List them and decide each with the owner: kept, or switched off.

## Passes when

- Each is recorded here with the decision.

## Closing

- **Passes:** record the build, the device and what was seen in this description, then close as completed.
- **Does not apply to {{system}}:** record why in this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one scope each), list them here, and close this once they are closed and the check passes.

## Learned in

`KairoTowns#5`
