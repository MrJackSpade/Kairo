---
title: Register {{app}} in Kairo's catalog_parts.py
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Required: build it for every new system.

## Applies when

Every new system.

## Work

In Kairo, `tools/catalog_parts.py` `APPS`: the app's product, platform slug, adult and main artwork part names and package title.

## Done when

- The entry is on Kairo's `main`, and `app_catalog.py` runs for {{app}}.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- This is required for every new system; it is never closed as not planned.
