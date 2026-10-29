---
title: Ship the free firmware and install it with the shared helper
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Implement, when it applies. Build it only if **Applies when** holds for {{system}}.

## Applies when

`{{ref:firmware}}` found free firmware {{app}} can ship.

## Work

- Gradle stages the firmware into the app's assets from `third_party/`.
- The app installs it into app storage with the shared shipped-asset install, which updates it only when it changed, never with copy code of its own.

## Done when

- A fresh install starts a game on the shipped firmware, and an update replaces changed firmware.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
- **Does not apply, or {{app}} already has it:** write what was checked and why into this description, then close as not planned.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- KairoST's `stageEmutos` task in `kairost/build.gradle.kts`.
