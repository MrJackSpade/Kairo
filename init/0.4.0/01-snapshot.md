---
title: Import the core as a pinned snapshot with its provenance
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Required: build it for every new system.

## Applies when

Every new system.

## Work

The core and the libraries it needs as pinned snapshots in `third_party/`, with their provenance in `docs/source-import.md`: URL, archive SHA-256, tag commit, date, exclusions, local changes.

## Done when

- `docs/source-import.md` records every snapshot.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- This is required for every new system; it is never closed as not planned.
