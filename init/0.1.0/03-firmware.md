---
title: Firmware: a free default the app ships, and the player's own
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system.

## Work

- List every firmware part the machine needs: system ROMs or BIOS, drive and controller ROMs, fonts.
- For each, look for a free replacement {{app}} can ship (its license checked as for the core). It is the default for every game, so the app works without the player's files.
- The player's own firmware can always be imported, and each game can choose it through its System setting, for games the free firmware doesn't run.
- For each part the player may import: its usual file names, its size, its SHA-1 where known, and how versions are told apart (a header field), so the import can recognize it.

## Done when

- This description lists every firmware part, its free replacement (or that none exists), and the player's files it accepts.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- KairoST ships EmuTOS and accepts the player's TOS for each model (`StTos.kt`).
- Kairo64 ships the MEGA65 Open ROMs and accepts the player's KERNAL, BASIC, character and 1541 ROMs (`C64Firmware.kt`).
- KairoMSX ships C-BIOS, which runs cartridges only; disk games use the player's MSX firmware.
