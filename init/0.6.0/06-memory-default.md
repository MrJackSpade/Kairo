---
title: Memory defaults as high as games allow
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Implement, when it applies. Build it only if **Applies when** holds for {{system}}.

## Applies when

The machine came with more than one memory size.

## Work

- A memory machine option defaulting to the most memory the machine had, with a per-game override.
- Research, never a test of every game: from the machine survey and the sources (manuals, emulator notes, player reports), find whether more memory is known to break games. Test only a game there is a reason to suspect.

## Done when

- The default and the per-game override reach the machine, checked once, and the games known to need less have it in the catalog. This description lists what the research found.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
- **Does not apply, or {{app}} already has it:** write what was checked and why into this description, then close as not planned.
