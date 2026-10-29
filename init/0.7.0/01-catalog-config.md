---
title: The catalog configuration
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system.

## Work

`tools/catalog_config.py`, which the shared catalog tools read: `APP`, `PLATFORMS`, `ARTWORK_CODE`, the media `SUFFIXES`, size `LIMITS` and `MEDIA` kinds, `MOBYGAMES`, the standard sets' `SCANS`, `MACHINE_OPTIONS` and `MACHINE()` (a release's settings), `BROKEN_DISK`, `BOOT_KINDS`, `COMPANION`, `DISK_SWAPS`, `MEDIA_EXTENSIONS` and `LAUNCH_FIELDS`.

## Done when

- `shared/tools/check-app.sh` and `app_catalog.py` run with it.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- Kairo64's `tools/catalog_config.py`.
