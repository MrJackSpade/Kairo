---
title: PC test: a hard disk boots, and what the guest writes stays in a new session
kind: validation
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Validation. Check it; build nothing unless the check fails.

## Applies when

The machine has hard disks ({{ref:hard-disks}}).

## Check

The shared check `hardDisk` (Kairo#123): the hard disk boots, the guest writes a file (typed commands), the session ends, and a new session on the same working copy reads it back. The check is written once, in the shared PC boot test ({{ref:boot-test}}); this ticket writes no test of its own. It gives {{app}}'s entries in `core/test/boot-tests.json` and makes them pass:

- A hard disk image of each kind the app boots (test media under `host/` when only this test needs it), and the commands that write and read back the file.

## Passes when

- {{app}}'s entries pass, and breaking {{app}}'s side of what the check looks at fails them.

## Closing

- **Passes:** write the entries (commits) and what the check reported into this description, then close as completed.
- **Does not apply:** write why into this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one problem each), list them here, and close this one when they are closed and the entries pass.
