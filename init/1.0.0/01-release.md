---
title: Release {{app}}
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Implement: build it when it applies, as **Applies when** says.

## Applies when

The owner decides to release {{app}}.

## Work

`docs/releases/v<version>.md`, `shared/tools/tag-release.ps1`, the Play Console app with the Play publishing service account invited, and the workload identity condition accepting `{{repo}}`.

## Done when

- The release is published on GitHub and Google Play.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- **Does not apply to {{system}}, or {{app}} already has it:** record what was checked and why in this description, then close as not planned.
