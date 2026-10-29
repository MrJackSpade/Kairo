---
title: Hard disk images
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Implement: build it when it applies, as **Applies when** says.

## Applies when

The real machine had hard disks.

## Work

Mount the image on the machine's own bus (with the controller's firmware, `{{ref:firmware}}`) and boot from it when the player starts it.

## Done when

- A hard disk image in the games folder is listed, starts and boots, and the Disks page shows it.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- **Does not apply to {{system}}, or {{app}} already has it:** record what was checked and why in this description, then close as not planned.

## Learned in

`KairoTowns#38`, `KairoST#73`, `Kairo64#52`, `KairoMSX#43`
