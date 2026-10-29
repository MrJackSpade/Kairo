---
title: Create a game from its installer media
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Implement, when it applies. Build it only if **Applies when** holds for {{system}}.

## Applies when

The machine installs games, to a hard disk for example.

## Work

- The shared Create game flow: the player picks installer media, the installer runs on a new disk or folder, and the result becomes a library game.

## Done when

- A game created from installer media is listed and starts.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
- **Does not apply, or {{app}} already has it:** write what was checked and why into this description, then close as not planned.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- KairoDos creates installed DOS games; Kairo98 creates bootable hard disk images.
