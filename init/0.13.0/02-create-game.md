---
title: A player creates a new game on a blank disk
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Implement, when it applies. Build it only if **Applies when** holds for {{system}}.

## Applies when

{{system}} writes games or their data to a disk the player makes: a hard disk a game installs to, a floppy formatted as a save disk, or a folder a DOS installer fills.

## Work

Work out, and build where it is missing, every step for a player to make a new game on {{app}} on the device:

- **Create:** a new blank disk image (hard disk, floppy or folder) in each format and size {{system}} used, made inside the app. This is a shared Create game flow in Kairo's `shared/`, not one app's own: the format and sizes come from each app. Build it in `shared` if no app has it yet.
- **Mount:** the blank disk goes in its drive next to the installer or system media (floppies, CD), and the machine starts from the right one.
- **Prepare:** format it, or make it bootable, the way {{system}} did (its own FORMAT or SYS command, or a game's own "make a save disk" option).
- **Install:** run the game's installer onto it, swapping installer disks as it asks.
- **Save:** the installed game, and what it writes later, stay on the new disk. The new disk becomes a library game that starts from itself, with its own settings and saves.

Write the steps into this description. The documentation ticket in this milestone documents them from here.

## Done when

- A game installed by a player onto a disk they created in the app is listed, starts from that disk, and keeps what it writes across closing and reopening.

## Closing

- **Done:** write what was built (commits), the steps and how they were verified on the device into this description, then close as completed.
- **Does not apply:** write why into this description, then close as not planned.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s machine may need it done differently.

- Kairo98: a blank HDI hard disk, formatted from an MS-DOS system floppy, then a game installed from its floppies.
- KairoDos: an empty C: drive, with the installer's floppy or CD images mounted and its INSTALL run.
- Kairo68: a blank HDF hard disk, formatted with Human68k's FORMAT, then a game installed.
- Kairo88: a blank D88 floppy, made a user or save disk by the game's own menu or formatted in N88-DISK BASIC.
- KairoMSX: a blank 720 KB DSK formatted in MSX-DOS, or a Nextor hard disk image, with a game copied to it.
- KairoST: a blank ST floppy or hard disk, with the game's hard disk installer run.
- Kairo64: a blank D64 formatted with the 1541's NEW command as a save disk, or a CMD HD image.
- KairoTowns: a blank SCSI hard disk, with a game installed from its CD under Towns OS.
