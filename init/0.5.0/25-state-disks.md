---
title: A save state puts back the disks it was saved with
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Implement: build it when it applies, as **Applies when** says.

## Applies when

The app has save states.

## Work

A state records which of the game's disks was in each drive and puts them back through `kairo_state_path` when it loads.

## Done when

- A state saved after a disk swap loads onto the disk it was saved with.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- **Does not apply to {{system}}, or {{app}} already has it:** record what was checked and why in this description, then close as not planned.

## Learned in

`Kairo98#94`, `Kairo68#69`, `Kairo64#49`
