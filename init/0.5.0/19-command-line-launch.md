---
title: Open a game at the guest's command line
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Implement: build it when it applies, as **Applies when** says.

## Applies when

The machine has a command line to open.

## Work

The core starts the game's machine with its media inserted and nothing autostarted, for the library's command-line action.

## Done when

- The action boots to the prompt with the game's media in the drives.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- **Does not apply to {{system}}, or {{app}} already has it:** record what was checked and why in this description, then close as not planned.

## Learned in

`Kairo#89`
