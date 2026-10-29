---
title: game_profile with frame hashes
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Required: build it for every new system.

## Applies when

Every new system.

## Work

`core/test/game_profile`: runs a game flat out with scripted input and reports host time per guest frame; `--hashes` and `check exact` show two builds run the guest alike. Builds for the host and the device.

## Done when

- It runs on the RG DS and two runs of one build match.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- This is required for every new system; it is never closed as not planned.
