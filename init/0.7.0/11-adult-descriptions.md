---
title: Adult descriptions for the adult games
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Implement: build it when it applies, as **Applies when** says.

## Applies when

The catalog marks adult games.

## Work

Frank adult descriptions in `descriptions-adult-v1.json` (`descriptions.py add --adult`), from real sources; the main description stays clean.

## Done when

- `audit_core_catalog.py` counts an adult description for every adult game with a source.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- **Does not apply to {{system}}, or {{app}} already has it:** record what was checked and why in this description, then close as not planned.

## Learned in

`Kairo68#71`, `KairoMSX#42`, `Kairo98#106`
