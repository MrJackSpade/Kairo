---
title: Prompt hooks for the shared startup typer
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Implement: build it when it applies, as **Applies when** says.

## Applies when

The machine boots to a prompt or command line (BASIC, DOS).

## Work

The core implements `prompt`, `typed_key`, `special_key` and `expand_command` for every prompt it boots to.

## Done when

- A game with startup commands starts with no key pressed.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- **Does not apply to {{system}}, or {{app}} already has it:** record what was checked and why in this description, then close as not planned.

## Learned in

`Kairo68#70`, `Kairo64#51`, `KairoMSX#41`
