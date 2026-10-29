---
title: Render at the machine's resolution and scale on the GPU
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Required: build it for every new system.

## Applies when

Every new system.

## Work

The core renders at the machine's own resolution; the GPU scales it. (GPU composition of planes and palettes is a performance ticket.)

## Done when

- The core hands 1x frames and the picture fills the screen as the scaling settings say.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- This is required for every new system; it is never closed as not planned.

## Learned in

`Kairo#50`
