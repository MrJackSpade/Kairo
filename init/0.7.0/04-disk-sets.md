---
title: Every multi-disk set is one library entry
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Implement, when it applies. Build it only if **Applies when** holds for {{system}}.

## Applies when

The standard sets hold games on more than one disk.

## Work

- Give every set a record that groups its disks, with their order, the boot disk, and any user or utility disks, even where nothing else is known about the game.
- Add a check, run in CI, that fails on any set left ungrouped (`disk_sets.ungrouped`).

## Done when

- No library entry is a single disk of a set, and the check passes.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
- **Does not apply, or {{app}} already has it:** write what was checked and why into this description, then close as not planned.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- Kairo68's library listed "Disk X of Y" entries for disks no record grouped (`Kairo68#3`).
