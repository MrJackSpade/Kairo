---
title: A save state restores the disks it was saved with
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Implement, when it applies. Build it only if **Applies when** holds for {{system}}.

## Applies when

The app has save states.

## Work

- A state records which of the game's disks was in each drive when it was saved, and puts those disks back (their working copies, through `kairo_state_path`) when it loads.

## Done when

- A state saved after a disk swap, loaded from a session started on the first disk, continues on the disk it was saved with.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
- **Does not apply, or {{app}} already has it:** write what was checked and why into this description, then close as not planned.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- KairoST's `StMachine::load_state`.
