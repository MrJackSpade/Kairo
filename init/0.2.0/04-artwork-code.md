---
title: Add the artwork code to both platform folder tables
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Required: build it for every new system.

## Applies when

Every new system.

## Work

- In Kairo, `tools/launchbox_artwork.py` `PLATFORM_FOLDERS` and `frontend/.../LaunchBoxArtworkReferences.kt` `PLATFORM_FOLDERS`, the same in both.
- Artwork comes from LaunchBox references, never a self-hosted copy of LaunchBox's images.

## Done when

- Both tables carry the code on Kairo's `main`.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- This is required for every new system; it is never closed as not planned.

## Learned in

`Kairo#36`, `KairoDos#85`
