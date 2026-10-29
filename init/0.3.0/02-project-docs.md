---
title: AGENTS.md, README, licenses and privacy policy
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Required: build it for every new system.

## Applies when

Every new system.

## Work

- `AGENTS.md`: the core credited without being the brand; no proprietary firmware or games; GitHub and Play builds alike; every catalog setting a per-game setting.
- `README.md` with the `kairo:` blocks, `COPYING`, and `LICENSE.md` and the privacy policy from `tools/docs.py`.

## Done when

- `shared/tools/docs.py . --check` passes.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- This is required for every new system; it is never closed as not planned.
