---
title: Add the platform to Kairo's LaunchBox and MobyGames tools
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Required: build it for every new system.

## Applies when

Every new system.

## Work

In Kairo, `tools/launchbox.py` and `tools/mobygames.py` `PLATFORMS`: the slug, the LaunchBox platform name and the MobyGames platform ID (`{{ref:catalog-identity}}`).

## Done when

- Both are on Kairo's `main`, and a LaunchBox snapshot and a MobyGames listing run for the platform.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- This is required for every new system; it is never closed as not planned.
