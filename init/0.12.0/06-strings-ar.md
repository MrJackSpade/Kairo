---
title: Arabic: translate {{app}}'s app strings
kind: translation
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system. Proper multilingual support is a hard requirement.

## Work

Depends on {{ref:strings-resources}}.

- Run `python shared/tools/translations.py check {{module}}/src/main/res`. It lists each Arabic string still holding the English, or translated from English that has since changed.
- Translate each in `{{module}}/src/main/res/values-ar/strings.xml`, and keep its `EN:` comment as the English it now translates.
- Use the words the shared frontend's Arabic already uses for the same things (`shared/frontend/src/main/res/values-ar/strings.xml`: library, game settings, firmware, disk, save state), and the other Kairo apps' Arabic for the settings they share (memory, CPU clock, Skip memory check), so the app and the frontend read as one.
- Keep unchanged: {{app}}, the emulator's and the machine's names, model names, key legends as printed on the hardware, file names and extensions. Games are games, and firmware is firmware or BIOS, never "ROM".
- Keep every format argument (`%1$s`, `%2$d`), and give each plural the quantities Arabic uses (`translations.py` lists them). Reorder a sentence around its arguments as Arabic needs; never split it into pieces.
- Arabic reads right to left. Check that the app's own rows mirror where the shared frontend's do, and that inserted values (numbers, file names, machine names) sit in the right place in the sentence.

## Done when

- `translations.py check` reports no Arabic string untranslated, stale or broken.
- On the RG DS set to Arabic, every screen and setting {{app}} adds (the first-run firmware step, the Machine page, game settings, the Disks page, error messages) shows Arabic with nothing cut off. This description lists the screens checked.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
