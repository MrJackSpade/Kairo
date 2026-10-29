---
title: Workflows calling Kairo at the pinned commit, secrets and webhooks
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Required: build it for every new system.

## Applies when

Every new system.

## Work

- `.github/workflows/`: `android.yml`, `tag-builds.yml`, `play-release.yml`, `discord-sync.yml`, `discord-release.yml`, calling Kairo at the pinned commit (`shared/tools/check-workflow-pins.sh`).
- The signing secrets (`KAIRO_RELEASE_KEYSTORE_B64`, `KAIRO_RELEASE_PASSWORD`) and the Discord webhooks.

## Done when

- A manual run of `android.yml` builds the debug APK, and opening an issue posts it to Discord.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- This is required for every new system; it is never closed as not planned.
