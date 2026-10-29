---
title: A machine that stops ends the game with its reason
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system.

## Work

- Every way the core can stop (a fatal error, a halted or jammed CPU, the emulator quitting) ends the game with a message saying why, and returns to the library.
- Never `exit()` or `abort()`, and never a silent stop that leaves the last frame on screen.

## Done when

- Each stop path, triggered on purpose, shows its reason and returns to the library.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- Kairo64 patches VICE so a CPU JAM ends the game with the reason (`tools/patches.py`, `VICE_JAM`).
