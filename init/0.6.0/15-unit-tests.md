---
title: Unit tests
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system.

## Work

- Tests for the keyboard mapping and the firmware identification, and the shared `CatalogSettingsTest`.
- The shared unit tests the app's machine calls for, each subclassed in the app's own tests: `LaunchTablesTest` (its launch tables), and `KanaLegendsTest` when the machine has a Japanese keyboard layout.

## Done when

- `testDebugUnitTest` passes.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
