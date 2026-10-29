---
title: Scan the standard sets
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system.

## Work

- Scan each standard set (`{{ref:game-sets}}`) with `shared/tools/scan_media.py` into `.downloads/scan/`.

## Done when

- Each scan index lists its set's media with their content IDs.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
