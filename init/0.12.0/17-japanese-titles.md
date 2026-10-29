---
title: Japanese: original Japanese titles in {{app}}'s catalog
kind: translation
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Implement, when it applies. Build it only if **Applies when** holds for {{system}}.

## Applies when

{{system}} games were released under Japanese titles.

## Work

- For every game released under a Japanese title, record that title in the catalog's Japanese title field.
- Take titles only from sources that state them: the catalog's Japanese aliases, LaunchBox's Japanese alternate names, VNDB's original titles, the game's own box or title screen. Never translate or romanize back: a game with no known Japanese title keeps its current one.
- Rebuild the catalog.

## Done when

- A device set to Japanese shows the Japanese title of every game that has one, and search finds a game by either title.
- This description gives how many were recorded and from which sources.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
- **Does not apply, or {{app}} already has it:** write what was checked and why into this description, then close as not planned.
