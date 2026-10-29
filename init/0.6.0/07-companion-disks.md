---
title: Find a game's other disks with the shared lookup
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Implement, when it applies. Build it only if **Applies when** holds for {{system}}.

## Applies when

The machine has more than one drive, or games come on several media used together.

## Work

- Find the media a game uses beside the one it starts from (a second drive's disk, a CD game's floppy) with the shared `companionFor`, which also reports a missing one.

## Done when

- A two-disk game starts with its second disk in place, and a missing disk is reported.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
- **Does not apply, or {{app}} already has it:** write what was checked and why into this description, then close as not planned.
