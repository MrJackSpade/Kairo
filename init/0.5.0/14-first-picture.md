---
title: Play reaches the game's first picture quickly
kind: validation
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Validation: check it. There is nothing to build unless the check fails.

## Applies when

Every new system.

## Check

Measure from Play to the first picture on the RG DS, split into extraction, firmware, emulator start and the guest's boot. The boot runs flat out; a bundled firmware's splash hold is cut.

## Passes when

- No black or splash screen holds for seconds before the game draws.

## Closing

- **Passes:** record the build, the device and what was seen in this description, then close as completed.
- **Does not apply to {{system}}:** record why in this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one scope each), list them here, and close this once they are closed and the check passes.

## Learned in

`Kairo68#2`, `KairoST#5`
