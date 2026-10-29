---
title: Host boot test
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Required: build it for every new system.

## Applies when

Every new system.

## Work

`core/test/boot_test.cpp`: several media in one process, scripted presses, a state saved and loaded during a load with its round trip checked, crashes and the last screen reported, frames kept; run with `shared/tools/test-core.ps1` under `run-limited.ps1`.

## Done when

- It boots the test set's games and reports each.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- This is required for every new system; it is never closed as not planned.
