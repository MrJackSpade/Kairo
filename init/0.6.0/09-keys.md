---
title: The guest keyboard layout
kind: implement
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Required: build it for every new system.

## Applies when

Every new system.

## Work

`<Sys>Keys.kt`: the layout from the shared blocks where they fit (`JisMainBlock`, the shared symbol page), legends following the running machine (international or JIS, with kana), the Android key mapping, labels, bindable keys and modifiers.

## Done when

- Every key types what its legend shows on each model.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- This is required for every new system; it is never closed as not planned.

## Learned in

`Kairo#49`, `KairoMSX#45`, `Kairo#81`
