---
title: Pin the registration commit in every Kairo app
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Required: build it for every new system.

## Applies when

Every new system.

## Work

Every Kairo app pins the Kairo commit that registers {{app}}, with its workflows calling that commit.

## Done when

- Every app's `shared/` is at or past the registration commit, and every app's README family grid lists {{app}}.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- This is required for every new system; it is never closed as not planned.
