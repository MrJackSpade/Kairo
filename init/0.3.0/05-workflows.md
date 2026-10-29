---
title: Workflows, secrets and webhooks
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system.

## Work

- `.github/workflows/`: `android.yml`, `tag-builds.yml`, `play-release.yml`, `discord-sync.yml` and `discord-release.yml`, each calling Kairo's reusable workflow at the pinned commit (`shared/tools/check-workflow-pins.sh` checks the pins).
- The repository secrets `KAIRO_RELEASE_KEYSTORE_B64` and `KAIRO_RELEASE_PASSWORD`, and the Discord webhooks the sync and release workflows use.

## Done when

- A manual run of `android.yml` builds the debug APK, and opening an issue posts it to Discord's issues forum.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
