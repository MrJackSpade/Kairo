---
title: The JNI bridge on the shared machine host
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Required: build it for every new system.

## Applies when

Every new system.

## Work

`{{module}}/src/main/cpp/<sys>_core.cpp` and its `CMakeLists.txt`, through `kairo_machine_library`, with `post` and `call`. State result codes come from `machine_core.h`, not the core's own.

## Done when

- The app starts a game through `NativeMachine`.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- This is required for every new system; it is never closed as not planned.

## Learned in

`Kairo#94`
