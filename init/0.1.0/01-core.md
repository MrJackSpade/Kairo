---
title: Choose the emulator core and verify its license
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Required: build it for every new system.

## Applies when

Every new system.

## Work

- Pick the emulator core for {{system}}.
- Verify its license from the source itself, file by file where files carry their own terms: GPL-2.0-or-later or permissive only.
- Never GPL-2.0-only (it cannot ship beside the shader runtime, see `shared/docs/licensing.md`), fmgen, or code under the pre-2016 non-commercial MAME license.
- List every file under other terms for replacement (as Hatari's three FS-UAE headers were).

## Done when

- This description names the core, its version and source, the license verdict, and every file to replace.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- This is required for every new system; it is never closed as not planned.
