---
title: A key tap reaches the guest at once and is read
kind: validation
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Validation: check it. There is nothing to build unless the check fails.

## Applies when

Every new system.

## Check

Tap keys on the bottom-screen keyboard at a prompt and in a game that waits for one key (a Y/N prompt).

## Passes when

- Each tap registers within half a second and the guest reads it.

## Closing

- **Passes:** record the build, the device and what was seen in this description, then close as completed.
- **Does not apply to {{system}}:** record why in this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one scope each), list them here, and close this once they are closed and the check passes.

## Learned in

`KairoST#3`, `KairoST#33`
