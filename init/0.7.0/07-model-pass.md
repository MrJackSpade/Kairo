---
title: Model pass: region and model by running the game
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Implement: build it when it applies, as **Applies when** says.

## Applies when

The machine came in more than one model or region.

## Work

Run each game on each, recording the model that runs it, never by the region in its name.

## Done when

- The catalog gives each game the model it runs on.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- **Does not apply to {{system}}, or {{app}} already has it:** record what was checked and why in this description, then close as not planned.

## Learned in

`Kairo64#14`, `Kairo64#13`
