---
title: PC test: guest input classifies known mouse and keyboard games
kind: validation
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Validation. Check it; build nothing unless the check fails.

## Applies when

The machine has a mouse ({{ref:input-telemetry}}).

## Check

The shared check `guestInput` (Kairo#117): after some frames, Auto touch mode, deciding from `guest_input` as the app does, is the expected mode. The check is written once, in the shared PC boot test ({{ref:boot-test}}); this ticket writes no test of its own. It gives {{app}}'s entries in `core/test/boot-tests.json` and makes them pass:

- A mouse game, decided mouse, and a keyboard game, decided keyboard, both from the test media (the same games the UI tests' `mouseGame` and `keyboardGame` name).

## Passes when

- {{app}}'s entries pass, and breaking {{app}}'s side of what the check looks at fails them.

## Closing

- **Passes:** write the entries (commits) and what the check reported into this description, then close as completed.
- **Does not apply:** write why into this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one problem each), list them here, and close this one when they are closed and the entries pass.
