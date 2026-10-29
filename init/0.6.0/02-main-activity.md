---
title: MainActivity on the shared MachineActivity
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system.

## Work

`MainActivity : MachineActivity` supplies:
- `identity` (names, accent color, credits, first-run texts);
- `machine` (resolution, aspect, state file name, mouse resolution);
- `bridge`, the `NativeMachine` with the lock keys;
- the keyboard, the Android-to-guest key mapping, the joystick controls and the `ControllerGuestSpec`;
- the `DiskImageLibrary.Format` (media kinds, labels, size limits, programs);
- `mediaSlots`, `firmwareSets`, `ownFirmwareInstalled`, `machineOptions`;
- `startGame`, which prepares the media and starts the machine.

## Done when

- A library game starts from the app.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- KairoST's and Kairo64's `MainActivity.kt`.
