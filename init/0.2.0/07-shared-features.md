---
title: Build in Kairo the features {{app}} needs
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Implement: build it when it applies, as **Applies when** says.

## Applies when

The survey (`{{ref:machine-survey}}`) found something the shared frontend or machine host lacks (a media kind, a `CatalogSetting`, a `DiskImageLibrary.Format` option, a firmware match, a `MachineCore` hook), or something another app has (startup commands, hard disks, clock, Skip memory check, disk swap automation).

## Work

- Build each in Kairo, for every app, never as a copy in {{app}}.
- A feature another app has is moved into Kairo where it is still that app's own, and both use the shared one.

## Done when

- Each feature listed here is in Kairo and used by {{app}}.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- **Does not apply to {{system}}, or {{app}} already has it:** record what was checked and why in this description, then close as not planned.

## Learned in

`Kairo#83`, `KairoTowns#39`
