---
title: Catalog identity: platform slug, LaunchBox, MobyGames and artwork code
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Required: build it for every new system.

## Applies when

Every new system.

## Work

- The platform slug (lower case, as `pc98`, `atarist`, `c64`).
- The LaunchBox platform name and the MobyGames platform ID.
- A new artwork code, unused by every other app (`shared/docs/catalog-research.md`); it is permanent once a catalog using it is published.

## Done when

- This description records all four.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- This is required for every new system; it is never closed as not planned.
