---
title: Ignore and attributes files
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system.

## Work

- `.gitignore` covering `bios/`, `roms/`, `.build/` and `.downloads/`, and every build output.
- `.gitattributes` as the other apps have it.
- `third_party/.gitignore` re-including everything, so the root rules don't drop files inside the snapshots (a folder named `build/`, for example).

## Done when

- A built checkout shows nothing in `git status`, and every file of each snapshot is tracked.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
