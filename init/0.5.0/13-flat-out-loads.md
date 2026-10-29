---
title: Run flat out while a game's media loads
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Implement: build it when it applies, as **Applies when** says.

## Applies when

The machine loads from a drive, tape or CD.

## Work

`booting()` is true while the guest loads from any medium, by its drive, tape or CD motor, not only during the first boot.

## Done when

- A disk, tape or CD load later in a game runs flat out and play returns to real speed after it.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- **Does not apply to {{system}}, or {{app}} already has it:** record what was checked and why in this description, then close as not planned.

## Learned in

`Kairo#82`, `Kairo64#2`
