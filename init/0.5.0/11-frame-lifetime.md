---
title: Frame buffers outlive the presenter's copies
kind: validation
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Validation: check it. There is nothing to build unless the check fails.

## Applies when

Every new system.

## Check

Check that every buffer the presenter thread copies stays valid until the copy ends, including through mode and resolution changes; play the game with the most mode changes known.

## Passes when

- No crash in a frame copy, and the code shows why none can happen.

## Closing

- **Passes:** record the build, the device and what was seen in this description, then close as completed.
- **Does not apply to {{system}}:** record why in this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one scope each), list them here, and close this once they are closed and the check passes.

## Learned in

`KairoTowns#7`
