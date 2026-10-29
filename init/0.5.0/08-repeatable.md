---
title: Runs repeat exactly
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Required: build it for every new system.

## Applies when

Every new system.

## Work

A repeatable mode (the clock from guest time, a fixed seed, synchronous media reads) and no uninitialized reads, so two runs of one build hash alike.

## Done when

- Two `game_profile ... check exact` runs of one build match.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- This is required for every new system; it is never closed as not planned.

## Learned in

`KairoTowns#32`
