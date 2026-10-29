---
title: Build the core as a library with the Kairo build flags
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Required: build it for every new system.

## Applies when

Every new system.

## Work

`core/CMakeLists.txt` builds the emulator as a library: `-fsigned-char`, `-O3` in every build type, `shared/cmake/KairoMachine.cmake` (hidden visibility), and `shared/cmake/KairoPgo.cmake` once a profile is recorded.

## Done when

- The core builds for the host and the device, optimized in Debug builds too.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- This is required for every new system; it is never closed as not planned.

## Learned in

`KairoDos#51`
