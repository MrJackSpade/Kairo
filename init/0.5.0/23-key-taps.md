---
title: A key tap reaches the guest at once and is read
kind: validation
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Validation. Check it; build nothing unless the check fails.

## Applies when

Every new system.

## Check

- Tap keys on the bottom-screen keyboard at a prompt, and in a game that waits for a single key (a Y/N question).

## Passes when

- Each tap registers within half a second, and is held long enough for the guest to read it.

## Closing

- **Passes:** write the build, the device and what was seen into this description, then close as completed.
- **Does not apply:** write why into this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one problem each), list them here, and close this one when they are closed and the check passes.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- KairoST: taps took seconds to register (`KairoST#3`), and a Y/N prompt ignored Y and N (`KairoST#33`).
