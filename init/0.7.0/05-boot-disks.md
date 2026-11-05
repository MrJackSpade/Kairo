---
title: Detect how each disk starts
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Implement, when it applies. Build it only if **Applies when** holds for {{system}}.

## Applies when

A disk can start by itself, start from a desktop or a prompt, or hold only data.

## Work

- A pass reads each disk image and records how it starts: by itself, from a program on it, or not at all (data).
- A data disk, or a set's second disk, never leads its set.
- A disk that boots to a desktop or prompt gets, in the catalog, the program or commands that start its game.

## Done when

- Every set records its boot disk.
- Starting is checked once for each way a disk starts (by itself, from a program on it, from a desktop or prompt), never for every set.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
- **Does not apply, or {{app}} already has it:** write what was checked and why into this description, then close as not planned.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- KairoST's `tools/boot_disks.py` reads boot sectors and AUTO folders (`KairoST#37`).
