---
title: Two runs of one build are identical
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system.

## Work

- A repeatable mode for tests: the guest's clock from guest time instead of the host's, a fixed random seed, and media reads that finish before the guest sees them.
- No uninitialized memory read by the emulation.

## Done when

- Two `game_profile ... check exact` runs of one build give the same hashes on several games.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- KairoTowns: `Config::repeatable`, and the uninitialized reads it uncovered (`KairoTowns#32`).
