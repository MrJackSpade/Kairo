---
title: Create the {{app}} repository with the pinned shared submodule
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Required: build it for every new system.

## Applies when

Every new system.

## Work

- `{{repo}}` with only the `origin` remote; no upstream remote, fork, other submodule or subtree.
- `shared/` pinned as a submodule; `git config core.hooksPath shared/githooks`.
- Author and committer MrJackSpade on every commit.

## Done when

- A clean clone with `git submodule update --init shared` builds the hooks' checks.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- This is required for every new system; it is never closed as not planned.
