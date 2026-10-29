---
title: Input telemetry for the Auto touch mode
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Required: build it for every new system.

## Applies when

Every new system.

## Work

The core reports the guest's keyboard waits and polls and mouse reads to the host's input telemetry, so Auto switches between Keyboard and Mouse.

## Done when

- In a mouse game, Auto switches to Mouse.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- This is required for every new system; it is never closed as not planned.

## Learned in

`Kairo#78`
