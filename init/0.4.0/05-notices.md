---
title: Third-party notices from the shared generator
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Required: build it for every new system.

## Applies when

Every new system.

## Work

`tools/notices_sources.py` gives the shared notices generator the app's license sources (core, embedded code, bundled firmware); CI checks the notices.

## Done when

- `THIRD_PARTY_NOTICES.txt` is generated and CI fails when it is stale.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- This is required for every new system; it is never closed as not planned.

## Learned in

`Kairo#86`
