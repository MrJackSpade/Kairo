---
title: Launch pass: boot every game on the host core
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system.

## Work

- Boot every library game on the host core, trying each setup (the shipped firmware, the player's, each drive or model) until one runs it.
- Press Start, Space and fire after the load and play past the title, since some games fail only once played.
- Judge by the picture (it changes, it isn't an error or a blank screen), not by sound.
- Record in the catalog research what each game needs (firmware, drive, model, the program to start), and the dumps nothing starts (`BROKEN_DISK`), which never lead their set.

## Done when

- The catalog gives every game the setup it runs on, and this description lists the games nothing starts.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- Kairo64's `tools/firmware_pass.py`; KairoST's `tools/tos_pass.py`.
