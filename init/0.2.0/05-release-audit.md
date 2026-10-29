---
title: Forbid {{system}} firmware and games in the release audit
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system. The work is in the Kairo repository (the shared frontend); this ticket tracks it for {{app}}.

## Work

- In Kairo's `tools/audit_android_release.py`, extend `FORBIDDEN` with the file names players give {{system}} firmware and with the extensions of its game media.
- The firmware {{app}} ships (`{{ref:firmware}}`) keeps a file name the pattern doesn't match.

## Done when

- The audit fails an APK holding a {{system}} firmware or game file, and passes {{app}}'s release APK.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
