---
title: The JNI bridge on the shared machine host
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system.

## Work

- `{{module}}/src/main/cpp/<machine>_core.cpp` and its `CMakeLists.txt`, through `kairo_machine_library`.
- Input and media changes go to the emulation thread with `post` and `call`.
- Save-state results use the shared codes in `machine_core.h`, not codes of the core's own.

## Done when

- The app starts, runs and ends a game through `NativeMachine`.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- KairoST's `kairost/src/main/cpp/st_core.cpp`.
