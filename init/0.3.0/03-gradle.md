---
title: Gradle project on the shared conventions
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Required: build it for every new system.

## Applies when

Every new system.

## Work

`settings.gradle.kts`, `build.gradle.kts`, `gradle.properties` and an executable `gradlew` delegating to shared (`shared/docs/android-build.md`).

## Done when

- `shared\tools\build.ps1` builds the project.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- This is required for every new system; it is never closed as not planned.
