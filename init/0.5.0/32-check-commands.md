---
title: PC test: startup commands are typed at each prompt and run
kind: validation
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Validation. Check it; build nothing unless the check fails.

## Applies when

The app types startup commands ({{ref:prompt-hooks}}).

## Check

The shared check `commands` (Kairo#119): the shared startup typer types the commands at the guest's prompt, and the answer's screen follows. The check is written once, in the shared PC boot test ({{ref:boot-test}}); this ticket writes no test of its own. It gives {{app}}'s entries in `core/test/boot-tests.json` and makes them pass:

- Each prompt the machine has (BASIC's, a DOS prompt from a disk or hard disk), each with a command whose answer is known and shown.

## Passes when

- {{app}}'s entries pass, and breaking {{app}}'s side of what the check looks at fails them.

## Closing

- **Passes:** write the entries (commits) and what the check reported into this description, then close as completed.
- **Does not apply:** write why into this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one problem each), list them here, and close this one when they are closed and the entries pass.
