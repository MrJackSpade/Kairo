---
title: A media slot for every drive, tape deck and cartridge port
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system.

## Work

- For each media kind in the survey, a slot the player can insert into and eject from during play.
- `media_paths()` answers from the emulator's own state, not from a record the app keeps, so the Disks page is right after a state load.

## Done when

- Every medium can be changed during play, and after a state load the Disks page shows the state's media.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- KairoTowns' Disks page showed the media from before a state load until it read them from Tsugaru (`KairoTowns#76`).
