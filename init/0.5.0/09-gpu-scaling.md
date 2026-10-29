---
title: Render at the machine's resolution and scale on the GPU
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system.

## Work

- The core hands the host frames at the machine's own resolution, without doubling or scaling them.
- The presenter scales them on the GPU, as the player's scaling settings say.
- Composing the picture on the GPU from the machine's planes and palettes is a performance ticket ("Perf: GPU composition of the guest picture").

## Done when

- The core's frames are 1x, and the picture fills the screen as each scaling setting says.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
