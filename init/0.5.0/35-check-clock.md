---
title: PC test: the machine runs at the clock it is set to
kind: validation
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Validation. Check it; build nothing unless the check fails.

## Applies when

The machine has a CPU clock setting.

## Check

The shared check `clock` (Kairo#122): a timing program (often typed `commands`) reaches its end screen in frames whose ratio between two settings of the clock is the one expected. The check is written once, in the shared PC boot test ({{ref:boot-test}}); this ticket writes no test of its own. It gives {{app}}'s entries in `core/test/boot-tests.json` and makes them pass:

- A timing loop at two settings of the clock, and the ratio expected (measured, where wait states keep it from the clocks' own ratio).

## Passes when

- {{app}}'s entries pass, and breaking {{app}}'s side of what the check looks at fails them.

## Closing

- **Passes:** write the entries (commits) and what the check reported into this description, then close as completed.
- **Does not apply:** write why into this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one problem each), list them here, and close this one when they are closed and the entries pass.
