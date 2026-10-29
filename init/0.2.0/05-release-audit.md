---
title: Forbid {{system}} firmware and media in the release audit
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Required: build it for every new system.

## Applies when

Every new system.

## Work

In Kairo, `tools/audit_android_release.py` `FORBIDDEN`: the system's firmware file names and media extensions. The bundled free firmware keeps a name of its own that the pattern allows.

## Done when

- The audit fails an APK holding a {{system}} firmware or game file and passes one holding only the bundled firmware.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- This is required for every new system; it is never closed as not planned.
