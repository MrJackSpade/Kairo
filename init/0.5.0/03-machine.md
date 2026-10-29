---
title: The machine: the frame host's Core and its configuration
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system.

## Work

- `core/<machine>_machine.cpp/.h` implements the `FrameMachine` Core (Kairo `docs/machine-host.md`): `run_frame`, `frame` or `gpu_picture`, `frame_seconds`, `audio_rate`, `read_audio`, `release_inputs`, `close`, `error`.
- A configuration with everything a game can set: model, region, memory, clock, firmware, drives, Skip memory check, and the core's own per-game options.

## Done when

- The host test boots a game through it.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- KairoST's `core/st_machine.h`, Kairo64's `core/c64_machine.h`.
