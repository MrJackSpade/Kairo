---
title: Boot disk detection
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Implement: build it when it applies, as **Applies when** says.

## Applies when

A disk can start by itself, start from a desktop or prompt, or hold only data.

## Work

A pass reads each disk image and records how it starts. A data disk or a Disk 2 never leads a set; a disk that boots to a desktop or prompt gets the program or commands that start it.

## Done when

- Every set starts from its boot disk, and desktop disks start their game.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- **Does not apply to {{system}}, or {{app}} already has it:** record what was checked and why in this description, then close as not planned.

## Learned in

`KairoST#34`, `KairoST#36`, `KairoST#37`
