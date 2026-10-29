---
title: A media slot for every drive, tape deck and cartridge port
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Implement: build it when it applies, as **Applies when** says.

## Applies when

Always, for the media kinds the survey found.

## Work

Insert and eject per slot, and `media_paths()` answered from the emulator's own state, so the Disks page is right after a state load.

## Done when

- Every medium can be changed during play, and the Disks page shows the state's media after a load.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- **Does not apply to {{system}}, or {{app}} already has it:** record what was checked and why in this description, then close as not planned.

## Learned in

`KairoTowns#76`, `Kairo98#103`, `Kairo64#50`
