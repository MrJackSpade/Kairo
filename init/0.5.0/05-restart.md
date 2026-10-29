---
title: Game after game in one process, with nothing carried over
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Required: build it for every new system.

## Applies when

Every new system.

## Work

A fresh copy of the library for each machine (KairoST's `hatari_library.h`) or patches that reset the emulator's global state. Nothing carries into the next machine: boot state, firmware (each machine loads its own files even when the last named the same), media, settings.

## Done when

- The host test runs several games in one process, a game after a loaded state among them, each as it runs alone.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- This is required for every new system; it is never closed as not planned.

## Learned in

`KairoST#32`, `Kairo64#11`
