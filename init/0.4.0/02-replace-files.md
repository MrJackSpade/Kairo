---
title: Replace the files under terms the app can't ship
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Implement, when it applies. Build it only if **Applies when** holds for {{system}}.

## Applies when

`{{ref:core}}` found files under terms {{app}} can't ship.

## Work

- Write a first-party replacement for each in `core/kairo/`, or leave the file out where nothing needs it.
- Record each in `docs/source-import.md` and `docs/licensing.md`.

## Done when

- None of the files is compiled into the app.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
- **Does not apply, or {{app}} already has it:** write what was checked and why into this description, then close as not planned.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- KairoST: `core/kairo/uae/` replaces three GPL-2.0-only headers of Hatari's.
