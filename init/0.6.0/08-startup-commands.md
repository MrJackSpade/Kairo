---
title: The startup commands setting
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Implement, when it applies. Build it only if **Applies when** holds for {{system}}.

## Applies when

The machine boots to a prompt (`{{ref:prompt-hooks}}`).

## Work

- The catalog schema gets the shared startup commands setting, and `startGame` passes it to the core (`StartupCommands.setting`). Like every catalog setting, it is also a per-game setting.

## Done when

- A game's startup commands, from the catalog or typed by the player, are typed at the prompt.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
- **Does not apply, or {{app}} already has it:** write what was checked and why into this description, then close as not planned.
