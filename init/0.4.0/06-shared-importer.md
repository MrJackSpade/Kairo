---
title: Import through the shared importer
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Implement, when it applies. Build it only if **Applies when** holds for {{system}}.

## Applies when

Kairo has an importer for this kind of source (such as the MAME subset importer).

## Work

- Import the snapshot with the shared tool, passing only what is {{app}}'s own (drivers, extra files, patches).

## Done when

- Re-importing gives the committed snapshot.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
- **Does not apply, or {{app}} already has it:** write what was checked and why into this description, then close as not planned.
