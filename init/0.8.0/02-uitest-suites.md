---
title: Every shared UI test suite
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system.

## Work

- Declare every shared suite as `<App><Suite>Tests` (`StateTests` only with save states). The media swap test runs with thousands of disks in the library.

## Done when

- Every shared suite is declared.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
