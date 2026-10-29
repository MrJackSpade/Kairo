---
title: Release {{app}}
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Implement, when it applies. Build it only if **Applies when** holds for {{system}}.

## Applies when

The owner decides to release {{app}}.

## Work

- `docs/releases/v<version>.md` with the release notes; every app releases the same version, tagged together with `shared/tools/tag-release.ps1`.
- For Google Play: the app in Play Console with the Play publishing service account invited, and the workload identity provider's condition accepting `{{repo}}`.

## Done when

- The release is published on GitHub, and on Google Play when it goes there.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
- **Does not apply, or {{app}} already has it:** write what was checked and why into this description, then close as not planned.
