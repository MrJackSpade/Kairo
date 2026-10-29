---
title: Move the guest's pointer to a touched point
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Implement, when it applies. Build it only if **Applies when** holds for {{system}}.

## Applies when

The machine has a mouse or another pointer.

## Work

- The core moves the guest's pointer to a point on the frame, so direct touch puts the pointer where the player tapped. A guest that only reads relative motion gets there by motion.

## Done when

- With direct touch, a tap puts the pointer under the finger in a mouse game.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
- **Does not apply, or {{app}} already has it:** write what was checked and why into this description, then close as not planned.
