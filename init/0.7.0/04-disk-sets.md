---
title: Every multi-disk set is one library entry
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Implement: build it when it applies, as **Applies when** says.

## Applies when

The standard sets hold multi-disk games.

## Work

Give every set a record at least good enough to group it (disk order, boot disk, user and utility disks), and add a check that fails on any ungrouped set (`disk_sets.ungrouped`).

## Done when

- No library entry is a single disk of a set.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- **Does not apply to {{system}}, or {{app}} already has it:** record what was checked and why in this description, then close as not planned.

## Learned in

`Kairo68#3`, `Kairo68#34`, `KairoST#2`
