---
title: Startup commands setting
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Implement: build it when it applies, as **Applies when** says.

## Applies when

The machine boots to a prompt (`{{ref:prompt-hooks}}`).

## Work

The catalog schema gains the shared startup commands setting, and `startGame` passes `StartupCommands.setting(...)`.

## Done when

- A game's startup commands, from the catalog or the player, are typed at the prompt.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- **Does not apply to {{system}}, or {{app}} already has it:** record what was checked and why in this description, then close as not planned.

## Learned in

`Kairo68#70`
