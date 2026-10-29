---
title: The catalog schema
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system.

## Work

- `<Machine>Catalog.kt`: the `LayeredGameCatalog` schema, with the machine options, media roles, launch fields, the media extensions removed before a name lookup, and the update URLs.

## Done when

- The shared `CatalogSettingsTest` passes.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
