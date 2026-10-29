---
title: The UI test descriptor
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system.

## Work

- `{{module}}/src/androidTest/.../uitest/<App>Tests.kt`: the `AppUnderTest` object (firmware files, the boot game and another game, a default option, a search term, the keys for the arrow, Return and Space tests, a media swap).

## Done when

- The UI test build compiles.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- Kairo64's `Kairo64Tests.kt`.
