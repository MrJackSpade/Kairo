---
title: Add {{app}}'s artwork code to both platform folder tables
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system. The work is in the Kairo repository (the shared frontend); this ticket tracks it for {{app}}.

## Work

- Add the artwork code and the slug to `PLATFORM_FOLDERS` in Kairo's `tools/launchbox_artwork.py` and in `frontend/src/main/java/.../LaunchBoxArtworkReferences.kt`. The two tables must be the same.
- The catalog references LaunchBox's images; it never hosts copies of them.

## Done when

- Both tables carry the code, and a catalog artwork reference for {{system}} expands to a LaunchBox image URL.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
