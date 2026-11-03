---
title: The shared PC boot test
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system.

## Work

{{app}}'s core runs on the shared PC boot test (Kairo `host-test/`, Kairo#115), which runs any app's `MachineCore` from a table the app keeps. {{app}} writes no boot test of its own:
- The core builds for the PC with `shared/tools/test-core.ps1` (llvm-mingw), with any Android-only piece behind the seams the other cores use (a picture the GPU composes, a library loaded per machine through `kairo/fresh_library.h`, Android logging).
- `core/test/boot-tests.json`: the test games, their media and settings as the app passes them, and the checks each runs (`boot` with `"audio"`, `state` with `"sound"`, and the checks the tickets after this one add). Its media come from the UI test resources (`uitest/resources.json`, with `host/` for files only this test uses); a `"home"` installs what the app installs in the core's home, and a setting `"home:<path>"` names such a file.
- What the app's own build makes that the core reads goes through `tools/prepare_host_test.py`.

## Done when

- `shared/tools/test-core.ps1` builds the core and runs its table, and every entry passes.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- Kairo98's, Kairo88's and KairoTowns's `core/test/boot-tests.json`; KairoMSX's `tools/prepare_host_test.py`; KairoST's Hatari library loaded per machine.
