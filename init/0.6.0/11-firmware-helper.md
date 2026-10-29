---
title: The firmware helper
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Implement: build it when it applies, as **Applies when** says.

## Applies when

Choosing the player's firmware needs more than `FirmwareSet` (versions by header, one per model).

## Work

A helper as Kairo64's `C64Firmware.kt` or KairoST's `StTos.kt`.

## Done when

- The right firmware is chosen for each game's model.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- **Does not apply to {{system}}, or {{app}} already has it:** record what was checked and why in this description, then close as not planned.
