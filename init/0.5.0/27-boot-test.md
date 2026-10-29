---
title: The host boot test
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system.

## Work

`core/test/boot_test.cpp`, run with `shared/tools/test-core.ps1` under `shared/tools/run-limited.ps1`:
- runs several media in one process, as the app runs one game after another;
- presses keys or buttons on a script;
- saves and loads a state during a load and checks the round trip;
- reports a crash and the last screen, and keeps the frames.

## Done when

- It boots the test set's games and reports each.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- KairoST's and Kairo64's `core/test/boot_test.cpp`.
