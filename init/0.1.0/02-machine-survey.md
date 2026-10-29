---
title: Survey the real {{system}} for parity
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system.

## Work

For the real {{system}}, and for the chosen core, find out:
- Each model and region, and what differs between them: CPU, clock, memory, video timing (PAL or NTSC), keyboard.
- The CPU clocks the machines shipped with or could switch to.
- The memory sizes they came with or could be expanded to.
- Every medium: floppy formats, hard disks, tapes, cartridges, CDs, programs run from a folder.
- Peripherals: mouse, joysticks and their ports, extra drives, hard disk controllers.
- Keyboards: the layout per region (international, JIS), kana, and the lock keys (Caps, Kana).
- What the machine boots to with no software: a prompt (BASIC, DOS), a desktop, or a wait for a disk.
- Whether its boot counts memory, and how long a boot takes.
- Settings or patches the core keeps per game.

## Done when

- This description has a table: each item above, what the real machine had, and whether the core supports it (yes, no, partly).
- The other tickets' **Applies when** are judged against this table.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- A survey of every app (2026-10-28) found CPU clock settings and hard disks missing from KairoST, KairoMSX and Kairo64 because nobody had checked whether the real machines had them.
