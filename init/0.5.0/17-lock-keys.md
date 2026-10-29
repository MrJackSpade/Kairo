---
title: Lock keys read back from the core
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Implement: build it when it applies, as **Applies when** says.

## Applies when

The machine has lock keys (Caps, Kana).

## Work

`locks()` reports them from the emulator's own state, and the app's `NativeMachine` and keyboard layout declare them.

## Done when

- After a state load, the keyboard lights the state's locks.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- **Does not apply to {{system}}, or {{app}} already has it:** record what was checked and why in this description, then close as not planned.

## Learned in

`Kairo#80`
