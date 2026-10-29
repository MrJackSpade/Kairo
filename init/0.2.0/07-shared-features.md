---
title: Build in Kairo what {{app}} needs from the shared frontend
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Implement, when it applies. Build it only if **Applies when** holds for {{system}}.

## Applies when

The survey (`{{ref:machine-survey}}`) or the work so far shows {{app}} needs something the shared frontend or machine host doesn't have (a media kind, a catalog setting, a library format option, a firmware match, a machine host hook), or something another Kairo app has its own copy of.

## Work

- Before building anything, look in every Kairo app for it.
- If another app has it, move it into Kairo, so that app and {{app}} both use the one shared version.
- Otherwise build it in Kairo, for every app, never as code of {{app}}'s own.

## Done when

- Each feature is listed here, is in Kairo, and {{app}} uses it.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
- **Does not apply, or {{app}} already has it:** write what was checked and why into this description, then close as not planned.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- Typed startup commands were Kairo88's own until KairoTowns needed them; they moved into Kairo (`startup_typer.h`, `StartupCommands`) for both.
- Disk swap automation lived only in Kairo98 and is being moved into Kairo for every app.
