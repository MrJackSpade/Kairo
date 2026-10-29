---
title: tools/catalog_config.py
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Required: build it for every new system.

## Applies when

Every new system.

## Work

`APP`, `PLATFORMS`, `ARTWORK_CODE`, `SUFFIXES`, `LIMITS`, `MEDIA`, `MOBYGAMES`, `SCANS`, `MACHINE_OPTIONS`, `MACHINE()`, `BROKEN_DISK`, `BOOT_KINDS`, `COMPANION`, `DISK_SWAPS`, `MEDIA_EXTENSIONS`, `LAUNCH_FIELDS`.

## Done when

- `shared/tools/check-app.sh` reads it.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- This is required for every new system; it is never closed as not planned.
