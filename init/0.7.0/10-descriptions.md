---
title: Original descriptions for every game
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system.

## Work

- Write an original description for every game with real reference text (`shared/tools/descriptions.py worklist`, then `add`). LaunchBox and MobyGames text is reference only and is never copied.
- No filler: a game with nothing real to say about it stays blank and is listed for research, never given "An action game by X."
- The main description never mentions adult content.

## Done when

- Every game with a real source has a description, and this description lists those left blank.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
