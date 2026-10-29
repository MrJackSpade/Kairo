---
title: The app module's build file
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Required: build it for every new system.

## Applies when

Every new system.

## Work

`{{module}}/build.gradle.kts`: `kairoProduct`, `kairoLabel`, `kairoCatalogRules`, `shared/gradle/kairo-app.gradle`, and the app's own CMake targets and assets.

## Done when

- The APK builds with the core.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- This is required for every new system; it is never closed as not planned.
