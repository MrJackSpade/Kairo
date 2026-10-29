---
title: A player adds, tests and configures a game they already have
kind: validation
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Validation. Check it; build nothing unless the check fails.

## Applies when

Every new system.

## Check

Work out every step a player takes on {{app}}, starting from a game they already own as files and ending with it playing as they want. Use each kind of media {{system}} has: floppy sets, hard disk images with their boot floppy, CDs, cartridges, tapes and folders, whichever it has. Do it on the device, as a player would, without adb shortcuts.

- **Add:** put the files where {{app}} reads them (the library folder, an archive, or another frontend opening a file), and the library picks the game up.
- **Find:** a catalog game shows its title and artwork by hash. A game the catalog doesn't know shows by its file name and starts the same way.
- **Firmware:** what the game needs, and what the player sees when it is missing.
- **Test:** the game boots and reaches play. Disk swaps work for a multi-disk set, and a hard disk game boots from its own disk.
- **Configure:** the game's own settings (machine model, memory, clock, boot media, startup commands), its controls (controller and on-screen), and its display and sound. Each setting is kept for that game alone.
- **Keep:** what the game writes (save files, a hard disk's changes) and save states survive closing and reopening the game.

Write the steps for each kind of media into this description. The documentation ticket in this milestone documents them from here.

## Passes when

- Every kind of media {{system}} has goes from files to a configured, playing game using only the app's own screens.
- What the game writes is still there after it is closed and reopened.

## Closing

- **Passes:** write the build, the device, the steps for each kind of media, and what was seen into this description, then close as completed.
- **Fails:** file each failure as its own ticket (one problem each), list them here, and close this one when they are closed and the check passes.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s machine may need it done differently.

- Kairo98: HDI/NHD hard disks with their boot floppy, D88/FDI floppy sets.
- KairoDos: game folders and ZIPs, and eXoDOS ZIPs.
- Kairo68: XDF/DIM floppies and HDF hard disks.
- Kairo88: D88 floppy sets and T88/CMT tapes.
- KairoMSX: ROM cartridges, DSK floppies and CAS tapes.
- KairoST: ST/MSA/STX floppies and GEMDOS hard disk folders.
- Kairo64: D64/G64 disks, T64/TAP tapes, PRG files and CRT cartridges.
- KairoTowns: CD images (CUE/CCD/MDS/ISO), D77 floppies and SCSI hard disks.
