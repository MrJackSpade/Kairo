---
title: MainActivity on MachineActivity
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Required: build it for every new system.

## Applies when

Every new system.

## Work

`MainActivity : MachineActivity`: `identity`, `machine` (resolution, aspect, state file, mouse resolution), `bridge` (with the lock keys), `keyboard`, `guestKey`, `joystickControls`, `controllerSpec`, `bindingsCodec`, the `DiskImageLibrary.Format` (kinds, labels, size limits, programs), `mediaSlots`, `firmwareSets` (by name, SHA-1 or header, and size), `ownFirmwareInstalled`, `machineOptions`, `startGame`.

## Done when

- A library game starts from the app.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- This is required for every new system; it is never closed as not planned.
