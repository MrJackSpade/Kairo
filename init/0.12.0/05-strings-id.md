---
title: Indonesian: translate {{app}}'s app strings
kind: translation
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Required: proper multilingual support is a hard requirement for every new system.

## Applies when

Every new system. Proper multilingual support is a hard requirement.

## Work

Depends on {{ref:strings-resources}}: every string the app shows is in `{{module}}/src/main/res/values/strings.xml`.

- Run `python shared/tools/translations.py check {{module}}/src/main/res`. It lists each Indonesian string that still holds the English or whose English has changed since it was translated (stale).
- Translate each in `values-in/strings.xml`, and keep its `EN:` comment as the English it now translates.
- Use the words the shared frontend's Indonesian already uses for the same things (`shared/frontend/src/main/res/values-in/strings.xml`: library, game settings, firmware, disk, save state), and the other Kairo apps' Indonesian strings for settings they share (memory, CPU clock, Skip memory check). The app and the frontend read as one product.
- Keep as they are: {{app}}, the emulator's and the machine's names, model names, key legends as printed on the hardware, file names and extensions. Games are games, and firmware is firmware or BIOS, never "ROM".
- Keep every format argument (`%1$s`, `%2$d`) and give each plural the quantities Indonesian uses (`translations.py` lists them). Reorder the sentence around an argument as Indonesian needs; never split it into pieces.
- Android's resource qualifier for Indonesian is `in`; the catalog's tag is `id`.

## Done when

- `translations.py check` reports no Indonesian string pending, stale or broken.
- On the RG DS set to Indonesian, every screen and setting {{app}} adds (first-run firmware step, Machine page, game settings, Disks page rows, error messages) shows Indonesian, with nothing cut off. This description lists the screens checked.

## Closing

- **Done:** record what was built (commits) and how it was verified in this description, then close as completed.
- This is required for every new system; it is never closed as not planned.
