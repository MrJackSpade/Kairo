---
title: The machine class: the FrameMachine Core and its configuration
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Required: build it for every new system.

## Applies when

Every new system.

## Work

`core/<sys>_machine.cpp/.h`: the `FrameMachine` Core (`shared/docs/machine-host.md`): `run_frame`, `frame` or `gpu_picture`, `frame_seconds`, `audio_rate`, `read_audio`, `release_inputs`, `close`, `error`; and the configuration (model, region, memory, clock, firmware, drives, Skip memory check).

## Done when

- The host test boots a game with it.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- This is required for every new system; it is never closed as not planned.
