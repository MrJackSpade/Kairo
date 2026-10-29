---
title: Hard disk images
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Implement, when it applies. Build it only if **Applies when** holds for {{system}}.

## Applies when

The real machine had hard disks.

## Work

- Mount a hard disk image on the machine's own bus, with the controller's firmware (`{{ref:firmware}}`), and boot from it when the player starts it.
- The library lists hard disk images, and the Disks page has a hard disk slot.

## Done when

- A hard disk image in the games folder is listed, starts and boots, and the Disks page shows it.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
- **Does not apply, or {{app}} already has it:** write what was checked and why into this description, then close as not planned.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- Kairo68's and KairoTowns' hard disks.
