---
title: Ignore and attribute files
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Required: build it for every new system.

## Applies when

Every new system.

## Work

`.gitignore` (`bios/`, `roms/`, `.build/`, `.downloads/`), `.gitattributes`, and `third_party/.gitignore` re-including everything the root rules would drop.

## Done when

- `git status` on a built checkout shows nothing generated, and `third_party/` keeps every file.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- This is required for every new system; it is never closed as not planned.
