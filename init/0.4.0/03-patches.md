---
title: Local changes as patches in the shared patch tool
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Implement: build it when it applies, as **Applies when** says.

## Applies when

The snapshot needs any change.

## Work

Every change is a patch in `tools/patches.py`, applied and checked by `shared/tools/local_patches.py`, run with `--check` in CI. Nothing is edited in the snapshot by hand.

## Done when

- `local_patches.py --check` passes and the snapshot matches its upstream archive apart from the patches.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- **Does not apply to {{system}}, or {{app}} already has it:** record what was checked and why in this description, then close as not planned.

## Learned in

`Kairo#92`
