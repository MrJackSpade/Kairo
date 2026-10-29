---
title: A machine that stops ends the game with its reason
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Required: build it for every new system.

## Applies when

Every new system.

## Work

A fatal error, a CPU halt or JAM, or the emulator quitting ends the game with its reason and returns to the library; never `exit()`, `abort()` or a silent stop on the last frame.

## Done when

- Each way the core can stop shows its reason and returns to the library.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- This is required for every new system; it is never closed as not planned.

## Learned in

`KairoST#7`
