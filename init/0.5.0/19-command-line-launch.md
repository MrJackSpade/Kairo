---
title: Open a game at the guest's command line
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Implement, when it applies. Build it only if **Applies when** holds for {{system}}.

## Applies when

The machine has a command line a player may want to open with a game's media.

## Work

- The core starts the machine with the game's media in its drives and nothing started, for the library's command-line action.

## Done when

- The action boots to the prompt with the game's media inserted.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
- **Does not apply, or {{app}} already has it:** write what was checked and why into this description, then close as not planned.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- KairoDos opens a game at the DOS prompt.
