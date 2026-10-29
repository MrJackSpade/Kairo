---
title: Register {{app}} in Kairo's apps.json
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system. The work is in the Kairo repository (the shared frontend); this ticket tracks it for {{app}}.

## Work

- Add {{app}}'s entry to Kairo's `docs/templates/apps.json`: `repo`, `system`, `module`, `package`, `googlePlay`, `firmware` (whether the player can import firmware), `saveStates`, `artworkHosts`, `privacyPage`, `problemDetail`, and `frontends` (how other Android frontends launch it) once known.
- `tools/docs.py` writes each app's license, privacy policy, frontend docs and README blocks from it, `tools/tag-release.ps1` releases every app it lists, and `tools/uitest-all.ps1` tests them.

## Done when

- Kairo's `python tools/docs.py . --check` passes, and the entry is on Kairo's `main`.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- KairoST's and Kairo64's entries in the same file.
