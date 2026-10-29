---
title: The Gradle project on the shared conventions
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system.

## Work

- `settings.gradle.kts` applying `shared/gradle/android-settings.gradle`, with the app module and `:frontend` mapped to `shared/frontend`.
- The root `build.gradle.kts`, `gradle.properties`, and an executable `gradlew` that delegates to shared's wrapper (Kairo `docs/android-build.md`).

## Done when

- `shared\tools\build.ps1` builds the project.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
