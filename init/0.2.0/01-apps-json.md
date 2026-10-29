---
title: Register {{app}} in Kairo's apps.json
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Required: build it for every new system.

## Applies when

Every new system.

## Work

In Kairo (`shared/`), `docs/templates/apps.json`: the app's entry (repo, system, module, package, googlePlay, firmware, saveStates, artworkHosts, privacyPage, problemDetail, frontends). It drives `tools/docs.py`, the family grid, `tools/tag-release.ps1` and `tools/uitest-all.ps1`.

## Done when

- Kairo's `tools/docs.py --check` passes with the entry, pushed to Kairo's `main`.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- This is required for every new system; it is never closed as not planned.
