---
title: Adult descriptions for the adult games
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Implement, when it applies. Build it only if **Applies when** holds for {{system}}.

## Applies when

The catalog marks adult games.

## Work

- A frank, factual adult description for each adult game with a real source, in `descriptions-adult-v1.json` (`descriptions.py add --adult`). It replaces the main description only in the adult part of the catalog.

## Done when

- `audit_core_catalog.py` counts an adult description for every adult game with a source.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
- **Does not apply, or {{app}} already has it:** write what was checked and why into this description, then close as not planned.
