---
title: Keep every change to the snapshots as a checked patch
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Implement, when it applies. Build it only if **Applies when** holds for {{system}}.

## Applies when

The core or a library needs any change to its source.

## Work

- Write each change as a patch in `tools/patches.py`; the shared `shared/tools/local_patches.py` applies them and, with `--check`, fails when the snapshot and the patches disagree.
- Run the check in `tools/checks.sh`, so CI fails on an unrecorded change.
- Never edit a snapshot by hand.

## Done when

- `local_patches.py --check` passes, and each patch has a line in `docs/source-import.md`.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
- **Does not apply, or {{app}} already has it:** write what was checked and why into this description, then close as not planned.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- Kairo64's `tools/patches.py` (its VICE patches, read as Latin-1).
