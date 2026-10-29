---
title: Register {{app}} in Kairo's catalog tools
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system. The work is in the Kairo repository (the shared frontend); this ticket tracks it for {{app}}.

## Work

- Add {{app}} to `APPS` in Kairo's `tools/catalog_parts.py`: the product name, the platform slug, the names of its adult and main artwork parts, and the artwork package's title.

## Done when

- `tools/app_catalog.py` builds {{app}}'s catalog parts.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- `'kairost': App('KairoST', 'atarist', 'kairost-art.nsfw', 'kairost-art', 'KairoST artwork', True)`
