---
title: Ship the bundled firmware and install it with the shared helper
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Implement: build it when it applies, as **Applies when** says.

## Applies when

`{{ref:firmware}}` found a free firmware to bundle.

## Work

Gradle stages it into the app's assets (as KairoST's `stageEmutos`), and the app installs it with the shared shipped-asset install, not its own copy logic.

## Done when

- A fresh install starts a game on the bundled firmware.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- **Does not apply to {{system}}, or {{app}} already has it:** record what was checked and why in this description, then close as not planned.

## Learned in

`Kairo#96`
