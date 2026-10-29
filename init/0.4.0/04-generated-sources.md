---
title: Commit the core's generated sources with their generator
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Implement: build it when it applies, as **Applies when** says.

## Applies when

The core generates sources at build time (Hatari's CPU core, VICE's infocontrib).

## Work

Commit them, with the generator in `tools/` and a `--check` in `tools/checks.sh`.

## Done when

- `tools/checks.sh` fails when the generated sources are stale.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- **Does not apply to {{system}}, or {{app}} already has it:** record what was checked and why in this description, then close as not planned.
