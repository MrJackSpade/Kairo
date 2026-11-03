---
title: App icon and accent color, drawn by the icon generator
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system.

## Work

Whoever implements this ticket makes the icon; the owner does not provide icon art. It is drawn by the shared icon generator (Kairo `tools/generate_app_icon.py`, `docs/app-icons.md`), never AI-generated or hand-painted, so it matches every other Kairo app.

- Add `tools/app_icon/presets/{{module}}.json` to the shared Kairo repo:
  - `theme`: the brand accent color (one no other app uses) and a frame color to go with it.
  - `logo`: the tag, the part of {{app}} after "Kairo".
  - `machine`: the {{system}} drawn from the generator's parts (monitor, tower, desktop unit, keyboard, mouse) and their details (labels, drive slots, lights, vents, badges), shaped like the real machine (see the machine survey ticket).
- Render it with `--out` and compare it with the other apps' icons (their presets render the same way); fix the preset until it sits with them.
- Write it into the app with `python shared/tools/generate_app_icon.py {{module}} --app .`: `{{module}}/src/main/res/drawable-nodpi/product_icon_art.png` and `docs/play-assets/app-icon.png`.
- The app's `Identity` takes the preset's accent as its brand accent color (an ARGB value).

## Done when

- The preset is in the shared Kairo repo, the generated icon art and Play icon are committed in {{repo}}, and this description records the accent color.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
