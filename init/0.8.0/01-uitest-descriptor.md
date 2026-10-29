---
title: UI test descriptor
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Required: build it for every new system.

## Applies when

Every new system.

## Work

The `AppUnderTest` descriptor (biosFiles, bootGame, otherGame, defaultOption, searchTerm, arrowKeys, returnKey, spaceKey, mediaSwap).

## Done when

- The UI test build compiles.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- This is required for every new system; it is never closed as not planned.
