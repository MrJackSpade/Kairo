---
title: A player adds a game the catalog doesn't know, sets it up and plays it
kind: validation
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Validation. Check it; build nothing unless the check fails.

## Applies when

Every new system.

## Check

The point is the player's own setup flow, not catalog matching. Use one game, with several disks where {{system}} has multi-disk games (a hard disk with its boot floppy, or a floppy set), whose content IDs are in no catalog layer: shipped, update, installed or user. A homebrew game, a player's own dump, or a game with no catalog record will do. Confirm the IDs are absent before starting. A game the catalog already knows doesn't count: catalog matching hides every step below.

Work out every step a player takes on {{app}}, on the device, as a player would and without adb shortcuts:

- **Add:** put the disks where {{app}} reads them (the library folder, an archive, or another frontend opening a file), and the library picks each one up.
- **Group:** the player puts the disks together into one game's set by hand. Run time never groups disks by file name: the player does it, and it is saved by content ID.
- **Name:** the player gives the game its title (and artwork, if the app takes it), and the library shows it as one game.
- **Boot disk:** the player chooses the disk it starts from, and which disk goes in each drive.
- **Firmware:** what the game needs, and what the player sees when it is missing.
- **Configure:** the game's own settings (machine model, memory, clock, startup commands), its controls (controller and on-screen), and its display and sound. Each setting is kept for that game alone.
- **Test:** the game boots from the chosen disk and reaches play, and swapping to its other disks works.
- **Keep:** the set, name, boot disk and settings survive closing and reopening the app and refreshing the library. What the game writes (save files, a hard disk's changes) and its save states survive too.

Write the steps into this description. The documentation ticket in this milestone documents them from here.

## Passes when

- The game goes from loose disks to one named, configured game that boots from its chosen disk and plays, using only the app's own screens.
- All of it is still there after the app is closed and reopened and the library refreshed.

## Closing

- **Passes:** write the build, the device, the game used (and how its IDs were confirmed absent), the steps and what was seen into this description, then close as completed.
- **Fails:** file each failure or missing step as its own ticket (one problem each), list them here, and close this one when they are closed and the check passes.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s machine may need it done differently.

- Kairo98: an HDI/NHD hard disk with its boot floppy, or a D88/FDI floppy set.
- KairoDos: a game folder or ZIP.
- Kairo68: an XDF/DIM floppy set, or an HDF hard disk.
- Kairo88: a D88 floppy set, or a T88/CMT tape.
- KairoMSX: a DSK floppy set, a ROM cartridge, or a CAS tape.
- KairoST: an ST/MSA/STX floppy set, or a GEMDOS hard disk folder.
- Kairo64: a D64/G64 disk set, a T64/TAP tape, a PRG file, or a CRT cartridge.
- KairoTowns: a CD image (CUE/CCD/MDS/ISO) with its D77 floppies, or a SCSI hard disk.
