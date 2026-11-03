---
title: PC test: several sessions in one process, including a start at the prompt
kind: validation
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Validation. Check it; build nothing unless the check fails.

## Applies when

Every new system.

## Check

The shared check `sessions` (Kairo#118): one process starts a game, ends it and starts it again, and the second session runs as the first; then the start at the prompt (`prompt`: the media and settings the app passes for it), for an app with one, runs as long. The check is written once, in the shared PC boot test ({{ref:boot-test}}); this ticket writes no test of its own. It gives {{app}}'s entries in `core/test/boot-tests.json` and makes them pass:

- A game {{app}} runs, and its start at the prompt with the settings {{app}} passes for that start.

## Passes when

- {{app}}'s entries pass, and breaking {{app}}'s side of what the check looks at fails them.

## Closing

- **Passes:** write the entries (commits) and what the check reported into this description, then close as completed.
- **Does not apply:** write why into this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one problem each), list them here, and close this one when they are closed and the entries pass.
