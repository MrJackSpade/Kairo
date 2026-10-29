---
title: A game's other disks through the shared lookup
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Implement: build it when it applies, as **Applies when** says.

## Applies when

The machine has more than one drive, or games come in sets.

## Work

Find the disks a game uses beside the one it starts from with the shared `companionFor`.

## Done when

- A two-disk game starts with its second disk in place, and a missing one is reported the shared way.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- **Does not apply to {{system}}, or {{app}} already has it:** record what was checked and why in this description, then close as not planned.

## Learned in

`Kairo#84`
