---
title: The app module's build file
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system.

## Work

- `{{module}}/build.gradle.kts` sets `kairoProduct`, `kairoLabel` and `kairoCatalogRules`, then applies `shared/gradle/kairo-app.gradle`.
- It adds only what is {{app}}'s own: extra CMake targets, staged assets.

## Done when

- The APK builds with the core.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- `kairost/build.gradle.kts`.
