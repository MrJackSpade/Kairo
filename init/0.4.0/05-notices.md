---
title: Third-party notices from the shared generator
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system.

## Work

- `tools/notices_sources.py` gives the shared notices generator {{app}}'s license sources: the core, the code it embeds, the firmware the app ships.
- The generator writes `{{module}}/src/main/assets/THIRD_PARTY_NOTICES.txt`, and CI checks it is current.

## Done when

- The notices name every component and its license, and CI fails when they are stale.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- Kairo64's `tools/notices_sources.py`.
