---
title: UI test resources
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system.

## Work

- `uitest/resources.json` naming the test games and firmware, and the encrypted bundle `uitest/TestResources.part1.dat` (keyed from `LOCAL_BUILD_TOKEN`).

## Done when

- The UI test build carries its games and firmware.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
