---
title: Firmware: the bundled free default and the player's own
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Required: build it for every new system.

## Applies when

Every new system.

## Work

- Find the free firmware the app can bundle as the default (EmuTOS, the Open ROMs, C-BIOS), with its license.
- List the player's own firmware the app imports: names, sizes, SHA-1 where known, and how to identify a version by its header.
- Peripherals' firmware too (hard disk controllers, drive ROMs).
- The player's firmware is a per-game System choice, never a replacement for every game.

## Done when

- This description names the bundled firmware (or that none exists) and every firmware file the player can import.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- This is required for every new system; it is never closed as not planned.

## Learned in

`Kairo98#107`, `KairoMSX#43`
