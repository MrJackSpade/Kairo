---
title: Import the core as a pinned snapshot
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system.

## Work

- Copy the core, and each library it needs, into `third_party/` from a released archive or tag, unchanged.
- Record each in `docs/source-import.md`: the URL, the archive's SHA-256, the tag's commit, the date, what was left out, and the local changes (kept as patches: the "Keep every change to the snapshots as a checked patch" ticket).

## Done when

- `docs/source-import.md` records every snapshot, and each matches its archive apart from the recorded exclusions and patches.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
