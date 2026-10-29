---
title: CPU clock setting
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Implement: build it when it applies, as **Applies when** says.

## Applies when

The real machine ran at more than one clock, or the core can run faster.

## Work

A numeric clock machine option with the real clocks, per-game override, catalog value winning over the default.

## Done when

- The chosen clock reaches the machine.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- **Does not apply to {{system}}, or {{app}} already has it:** record what was checked and why in this description, then close as not planned.

## Learned in

`Kairo68#4`, `KairoMSX#44`, `KairoST#72`
