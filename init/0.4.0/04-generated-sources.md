---
title: Commit the core's generated sources with their generator
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Implement, when it applies. Build it only if **Applies when** holds for {{system}}.

## Applies when

The core generates source files at build time (CPU emulation tables, version headers).

## Work

- Commit the generated files, so the Android build needs no host tools.
- Add the generator to `tools/`, and a `--check` of it to `tools/checks.sh`.
- Document how to regenerate them in `docs/development.md`.

## Done when

- `tools/checks.sh` fails when the generated files are stale.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
- **Does not apply, or {{app}} already has it:** write what was checked and why into this description, then close as not planned.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- KairoST commits Hatari's generated CPU core in `core/generated/cpu/` (`tools/generate_cpu.py`).
- Kairo64 commits VICE's `infocontrib.h` (`tools/generate_infocontrib.sh`).
