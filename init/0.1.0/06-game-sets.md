---
title: Standard game sets and the test set
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Required: build it for every new system.

## Applies when

Every new system.

## Work

- Name the standard sets the catalog scans (TOSEC, No-Intro and the like), which players will have too.
- Put a test set in the app's ignored `roms/` and `bios/` folders with one game of every media kind, a multi-disk game, a mouse game, a game that reads the cursor keys, a game on the player's firmware and the heaviest action game known.

## Done when

- This description lists the standard sets and the test set, with what each test game covers.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- This is required for every new system; it is never closed as not planned.
