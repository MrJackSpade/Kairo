---
title: Korean: translate {{app}}'s app strings
kind: translation
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system. Proper multilingual support is a hard requirement.

## Work

Depends on {{ref:strings-resources}}.

- Run `python shared/tools/translations.py check {{module}}/src/main/res`. It lists each Korean string still holding the English, or translated from English that has since changed.
- Translate each in `{{module}}/src/main/res/values-ko/strings.xml`, and keep its `EN:` comment as the English it now translates.
- Use the words the shared frontend's Korean already uses for the same things (`shared/frontend/src/main/res/values-ko/strings.xml`: library, game settings, firmware, disk, save state), and the other Kairo apps' Korean for the settings they share (memory, CPU clock, Skip memory check), so the app and the frontend read as one.
- Keep unchanged: {{app}}, the emulator's and the machine's names, model names, key legends as printed on the hardware, file names and extensions. Games are games, and firmware is firmware or BIOS, never "ROM".
- Keep every format argument (`%1$s`, `%2$d`), and give each plural the quantities Korean uses (`translations.py` lists them). Reorder a sentence around its arguments as Korean needs; never split it into pieces.

## Done when

- `translations.py check` reports no Korean string untranslated, stale or broken.
- On the RG DS set to Korean, every screen and setting {{app}} adds (the first-run firmware step, the Machine page, game settings, the Disks page, error messages) shows Korean with nothing cut off. This description lists the screens checked.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
