---
title: The on-screen keyboard
kind: validation
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Validation: check it. There is nothing to build unless the check fails.

## Applies when

Every new system, on the RG DS, each finding its own ticket.

## Check

Tap keys at a prompt and in a game; chorded keys; Caps and Kana after a state load.

## Passes when

- Taps register at once, chords light only their key, locks light as the state has them.

## Closing

- **Passes:** record the build, the device and what was seen in this description, then close as completed.
- **Does not apply to {{system}}:** record why in this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one scope each), list them here, and close this once they are closed and the check passes.

## Learned in

`KairoST#3`, `Kairo64#3`
