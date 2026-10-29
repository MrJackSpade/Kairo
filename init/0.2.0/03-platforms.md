---
title: Add {{system}} to Kairo's LaunchBox and MobyGames tools
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system. The work is in the Kairo repository (the shared frontend); this ticket tracks it for {{app}}.

## Work

- `tools/launchbox.py` `PLATFORMS`: the slug and the LaunchBox platform name.
- `tools/mobygames.py` `PLATFORMS`: the slug and the MobyGames platform ID.
- Both from {{ref:catalog-identity}}.

## Done when

- A LaunchBox snapshot and a MobyGames listing run for the platform.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
