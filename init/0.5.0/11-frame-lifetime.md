---
title: Frame buffers stay valid while the presenter copies them
kind: validation
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Validation. Check it; build nothing unless the check fails.

## Applies when

Every new system.

## Check

- Follow each buffer from the core to the presenter thread: it must stay valid until the copy ends, through resolution and mode changes too.
- Play the game known for the most mode changes.

## Passes when

- Nothing crashes in a frame copy, and the code shows why nothing can.

## Closing

- **Passes:** write the build, the device and what was seen into this description, then close as completed.
- **Does not apply:** write why into this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one problem each), list them here, and close this one when they are closed and the check passes.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- KairoTowns: Super Street Fighter II crashed the app in a frame copy (`KairoTowns#7`).
