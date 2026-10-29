---
title: The emulator's per-game settings reach the machine
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Implement: build it when it applies, as **Applies when** says.

## Applies when

The emulator keeps settings or patches per game (Tsugaru's `-APP`).

## Work

Give them a catalog setting (a per-game setting too) and pass it to the machine.

## Done when

- A game that needs one runs correctly with it.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- **Does not apply to {{system}}, or {{app}} already has it:** record what was checked and why in this description, then close as not planned.

## Learned in

`KairoTowns#77`
