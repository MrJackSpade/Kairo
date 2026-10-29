---
title: Survey the real {{system}} for parity
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Required: build it for every new system.

## Applies when

Every new system.

## Work

List, for the real machine and for the chosen core:
- Models and regions (PAL/NTSC timing), CPU clocks, memory sizes.
- Every media kind: floppies, hard disks, tapes, cartridges, CDs, programs.
- Peripherals: mouse, joystick ports, drives, hard disk controllers.
- Keyboards: international and JIS layouts, kana, lock keys (Caps, Kana).
- Whether the boot counts memory, and what command line or prompt it boots to (BASIC, DOS).
- Settings or patches the emulator keeps per game.

## Done when

- This description lists each of the above with whether the real machine had it and whether the core supports it.
- Later tickets judge their **Applies when** against this list.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- This is required for every new system; it is never closed as not planned.

## Learned in

`Kairo88#2`, `Kairo64#50`, `KairoMSX#45`, `Kairo#81`, the 2026-10-28 parity survey (`KairoST#72`, `KairoST#73`, `Kairo64#52`)
