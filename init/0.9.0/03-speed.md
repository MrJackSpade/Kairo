---
title: Full speed on the heaviest games
kind: validation
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Validation: check it. There is nothing to build unless the check fails.

## Applies when

Every new system, on the RG DS, each finding its own ticket.

## Check

Play the heaviest action games for minutes, not still screens, reading the frame reports; measure the flat-out speed during a load. A still screen should cost little; crackle is underruns.

## Passes when

- The guest's frame rate with no audio underruns.

## Closing

- **Passes:** record the build, the device and what was seen in this description, then close as completed.
- **Does not apply to {{system}}:** record why in this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one scope each), list them here, and close this once they are closed and the check passes.

## Learned in

`KairoTowns#6`, `Kairo64#1`, `Kairo68#30`, `Kairo68#29`
