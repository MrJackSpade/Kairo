---
title: Prompt hooks for typed startup commands
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Implement, when it applies. Build it only if **Applies when** holds for {{system}}.

## Applies when

The machine boots to a prompt or command line (BASIC, DOS) with some games.

## Work

- The core implements the shared startup typer's hooks (`prompt`, `typed_key`, `special_key`, `expand_command`) for every prompt the machine boots to, so a game's startup commands are typed when the prompt appears.

## Done when

- A game with startup commands boots to the prompt and starts with no key pressed.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
- **Does not apply, or {{app}} already has it:** write what was checked and why into this description, then close as not planned.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- Kairo88's N88-BASIC prompt and KairoTowns' DOS prompt, on the shared `startup_typer.h`.
