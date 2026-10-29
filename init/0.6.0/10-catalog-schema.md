---
title: The catalog schema
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Required: build it for every new system.

## Applies when

Every new system.

## Work

`<Sys>Catalog.kt`: the `LayeredGameCatalog` schema (options, media roles, launch fields, `mediaExtensions`, update URLs).

## Done when

- The shared `CatalogSettingsTest` passes.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- This is required for every new system; it is never closed as not planned.
