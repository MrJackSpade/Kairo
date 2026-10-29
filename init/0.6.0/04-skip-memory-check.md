---
title: Skip memory check (default on)
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Implement: build it when it applies, as **Applies when** says.

## Applies when

The boot counts memory.

## Work

A Skip memory check machine option, on by default, with the same name and place as in the other apps.

## Done when

- With it on, the boot skips the memory count.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- **Does not apply to {{system}}, or {{app}} already has it:** record what was checked and why in this description, then close as not planned.

## Learned in

`Kairo98#62`, `Kairo68#1`, `KairoTowns#2`, `KairoST#1`
