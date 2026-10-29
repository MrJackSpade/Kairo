---
title: Build the core as a library with the Kairo build settings
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system.

## Work

- `core/CMakeLists.txt` builds the emulator as a library for the host and for Android.
- `-fsigned-char` (cores written for x86 assume a signed `char`; ARM's is unsigned), and `-O3` in every build type, Debug included (an unoptimized core runs far below real time).
- `shared/cmake/KairoMachine.cmake` for hidden symbol visibility, and `shared/cmake/KairoPgo.cmake` once a profile is recorded (the "Perf: Profile-guided optimization" ticket).

## Done when

- The core builds for the host and the RG DS, optimized in every build type.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
