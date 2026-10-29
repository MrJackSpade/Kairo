---
title: game_profile: speed and exactness on host and device
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system.

## Work

`core/test/game_profile`, built for the host and for the RG DS:
- runs a game flat out with scripted input and reports host time per guest frame;
- with `--hashes` writes every frame's picture and sound hash; `check exact` compares two builds.

## Done when

- It runs on the RG DS, and two runs of one build match.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- KairoST's `core/test/game_profile` and `tools/perf/`.
