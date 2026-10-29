---
title: Japanese: original Japanese titles in {{app}}'s catalog
kind: translation
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Implement: build it when it applies, as **Applies when** says.

## Applies when

{{system}} games were released under Japanese titles.

## Work

- For every game in the catalog released under a Japanese title, record that title in the Japanese title field (`Kairo#61`).
- Take titles only from sources that state them: the catalog's Japanese aliases, LaunchBox's Japanese alternate names, VNDB's original titles, the game's own box or title screen. Never translate or romanize back: a game with no known Japanese title keeps its current one.
- Rebuild the catalog.

## Done when

- A device set to Japanese shows the Japanese title for every game that has one, and search finds a game by either title.
- This description gives the count recorded and the sources used.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- **Does not apply to {{system}}, or {{app}} already has it:** record what was checked and why in this description, then close as not planned.
