---
title: Create the {{app}} repository
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system.

## Work

- Create `{{repo}}`, with only the `origin` remote: no upstream remote, fork relationship, other submodule or subtree.
- Add Kairo as the `shared/` submodule and run `git config core.hooksPath shared/githooks`.
- Every commit's author and committer is MrJackSpade, with no co-author or tool lines (the hooks check it).

## Done when

- A fresh clone with `git submodule update --init shared` has the hooks in place.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
