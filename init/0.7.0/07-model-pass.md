---
title: Decide each game's model and region by running it
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Implement, when it applies. Build it only if **Applies when** holds for {{system}}.

## Applies when

The machine came in more than one model or region (PAL and NTSC, for example).

## Work

- Run each game on each model and record the one that runs it. A region in a file name is not evidence.

## Done when

- The catalog gives each game the model it runs on.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
- **Does not apply, or {{app}} already has it:** write what was checked and why into this description, then close as not planned.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- Kairo64's `tools/model_pass.py`, which also reads the raster lines a game waits on.
