---
title: Pin the Kairo commit that registers {{app}} in every app
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system. The work is in the Kairo repository (the shared frontend); this ticket tracks it for {{app}}.

## Work

- In every Kairo app, update the `shared/` submodule to the commit that registers {{app}}, and the workflows' pinned Kairo commit with it.
- Regenerate each app's README blocks (`shared/tools/docs.py`) so their family grids list {{app}}.

## Done when

- Every app is pushed with that pin, and its README lists {{app}}.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
