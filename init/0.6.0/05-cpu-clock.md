---
title: CPU clock setting
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Implement, when it applies. Build it only if **Applies when** holds for {{system}}.

## Applies when

The real machine ran at more than one clock, or games benefit from running faster.

## Work

- A numeric clock machine option offering the real clocks, with a per-game override; a game's catalog value wins over the all-games default.

## Done when

- The chosen clock reaches the machine.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
- **Does not apply, or {{app}} already has it:** write what was checked and why into this description, then close as not planned.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- Kairo68 offers 10, 16 and 25 MHz.
