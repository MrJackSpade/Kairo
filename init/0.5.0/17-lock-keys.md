---
title: Report the lock keys from the core
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Implement, when it applies. Build it only if **Applies when** holds for {{system}}.

## Applies when

The machine has lock keys (Caps, Kana).

## Work

- The core's `locks()` reports them from the emulator's own state.
- The app's `NativeMachine` and the keyboard layout declare them.

## Done when

- After a state load, the on-screen keyboard lights the locks as the state has them.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
- **Does not apply, or {{app}} already has it:** write what was checked and why into this description, then close as not planned.
