---
title: Standard game sets and the test set
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system.

## Work

- **Standard sets:** the collections players of {{system}} are likely to have (TOSEC, No-Intro, well-known community sets). The catalog is built from these, so a player's files match it.
- **Test set**, in the app's ignored `roms/` and `bios/` folders:
  - one game of every media kind the app supports;
  - a multi-disk game that asks for a disk swap;
  - a game that boots to a desktop or a prompt instead of starting by itself;
  - a game that uses the mouse, if the machine has one;
  - a game that reads the cursor keys;
  - a game that needs the player's firmware;
  - the heaviest action game known for the machine;
  - the player's firmware.

## Done when

- This description lists the standard sets (with where they are kept locally) and each test game with what it covers.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
