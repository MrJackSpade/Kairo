---
title: Report what the guest reads, for the Auto touch mode
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system.

## Work

- The core reports the guest's keyboard waits and polls and its mouse reads through the machine host's input telemetry, so the Auto touch mode can switch between Keyboard and Mouse by what the game uses.

## Done when

- In a mouse game Auto switches to Mouse, and in a keyboard game it stays on Keyboard.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
