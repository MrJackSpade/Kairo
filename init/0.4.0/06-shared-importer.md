---
title: Import through the shared importer
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Implement: build it when it applies, as **Applies when** says.

## Applies when

Kairo has an importer for this source (the MAME subset importer).

## Work

Import with the shared tool, passing only what is {{app}}'s own.

## Done when

- Re-importing gives the committed snapshot.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- **Does not apply to {{system}}, or {{app}} already has it:** record what was checked and why in this description, then close as not planned.

## Learned in

`Kairo#93`
