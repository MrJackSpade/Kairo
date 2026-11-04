---
title: PC test: a state loads with the disks it was saved with
kind: validation
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Validation. Check it; build nothing unless the check fails.

## Applies when

The machine has a drive, tape or cartridge slot whose medium the Disks page can change.

## Check

The shared check `stateDisks` (Kairo#116): with a slot's medium in, swap it (or eject it), save, swap back and load; the load puts the state's medium back, as `media_paths()` reports, and an eject right before a save leaves the save working. The check is written once, in the shared PC boot test ({{ref:boot-test}}); this ticket writes no test of its own. It gives {{app}}'s entries in `core/test/boot-tests.json` and makes them pass:

- An entry for every slot that can change: each drive, the tape, the cartridge, the CD and the hard disk, with a multi-disk game from the test media where one exists. A slot the running machine cannot empty (a hard disk some cores keep in for the whole run) says `"eject": false` and swaps to another medium.

## Passes when

- {{app}}'s entries pass, and breaking {{app}}'s side of what the check looks at fails them.

## Closing

- **Passes:** write the entries (commits) and what the check reported into this description, then close as completed.
- **Does not apply:** write why into this description, then close as not planned.
- **Fails:** file each failure as its own ticket (one problem each), list them here, and close this one when they are closed and the entries pass.
