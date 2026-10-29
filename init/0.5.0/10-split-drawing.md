---
title: A frame's picture does not depend on where its drawing is split
kind: validation
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Validation: check it. There is nothing to build unless the check fails.

## Applies when

The core draws a frame in pieces (before changes mid-frame, then the rest).

## Check

Draw the same frames with and without the splits in `game_profile` and compare the hashes.

## Passes when

- The pictures match.

## Closing

- **Passes:** record the build, the device and what was seen in this description, then close as completed.
- **Does not apply to {{system}}:** record why in this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one scope each), list them here, and close this once they are closed and the check passes.

## Learned in

`Kairo68#33`
