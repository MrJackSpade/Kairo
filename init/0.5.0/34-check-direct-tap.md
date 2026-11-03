---
title: PC test: a direct tap puts the guest's pointer on the point and clicks there
kind: validation
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Validation. Check it; build nothing unless the check fails.

## Applies when

The machine has a mouse ({{ref:pointer-warp}}).

## Check

The shared check `directTap` (Kairo#121): after a warp to each point, the guest's pointer is there (`pointer=x,y` in `details()`), or for a guest with a pointer of its own (`ownPointer`), a click there shows what it selects; `mouseMotion` checks relative motion across the counter's wrap. The check is written once, in the shared PC boot test ({{ref:boot-test}}); this ticket writes no test of its own. It gives {{app}}'s entries in `core/test/boot-tests.json` and makes them pass:

- A mouse program, the points to tap (the corners and the middle), where the guest keeps its pointer, and one point whose click selects something.

## Passes when

- {{app}}'s entries pass, and breaking {{app}}'s side of what the check looks at fails them.

## Closing

- **Passes:** write the entries (commits) and what the check reported into this description, then close as completed.
- **Does not apply:** write why into this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one problem each), list them here, and close this one when they are closed and the entries pass.
