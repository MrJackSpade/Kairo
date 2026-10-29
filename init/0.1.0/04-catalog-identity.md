---
title: Catalog identity: platform slug, LaunchBox, MobyGames and artwork code
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system.

## Work

- The platform slug: short and lower case. Catalog keys are `<slug>:<id>`.
- The LaunchBox platform name, exactly as LaunchBox's `Metadata.zip` names it.
- The MobyGames platform ID.
- An artwork code no other app uses (the platforms table in Kairo `docs/catalog-research.md`). Installed apps derive artwork cache paths from it, so it is permanent once a catalog using it is published.

## Done when

- This description records all four.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- The Atari ST: slug `atarist`, LaunchBox "Atari ST", MobyGames 24, artwork code 83.
