---
title: Skip memory check, on by default
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Implement, when it applies. Build it only if **Applies when** holds for {{system}}.

## Applies when

The machine's boot counts memory.

## Work

- A Skip memory check machine option, on by default, under the same name and in the same place as in the other apps, that skips the count (or runs it as fast as the machine allows).

## Done when

- With it on, the boot skips the memory count.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
- **Does not apply, or {{app}} already has it:** write what was checked and why into this description, then close as not planned.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- KairoTowns sets Tsugaru's CMOS fast-boot flag; KairoST uses Hatari's fast boot.
