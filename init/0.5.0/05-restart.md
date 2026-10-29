---
title: Run game after game in one process, with nothing carried over
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system.

## Work

- Many emulators keep their state in process-wide variables. Either load a fresh copy of the core's library for each machine, or reset every piece of global state the core keeps.
- Nothing may carry into the next machine: boot state, firmware (each machine loads its own firmware files, even when the last machine named the same ones), media, settings, a loaded state's contents.

## Done when

- The host test runs several games in one process, a game after a loaded state among them, and each behaves as it does alone.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- KairoST loads a fresh copy of Hatari for each machine (`core/hatari_library.h`).
- In Kairo64, a game started after a loaded state ran on the state's ROMs until each machine loaded its own (`Kairo64#11`).
