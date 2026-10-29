---
title: Replace the files under incompatible terms
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Implement: build it when it applies, as **Applies when** says.

## Applies when

`{{ref:core}}` listed files under terms the app cannot ship.

## Work

Replace each with a first-party file in `core/kairo/` (as KairoST did for Hatari's FS-UAE headers), recorded in `docs/source-import.md` and `docs/licensing.md`.

## Done when

- No listed file is compiled into the app.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- **Does not apply to {{system}}, or {{app}} already has it:** record what was checked and why in this description, then close as not planned.
