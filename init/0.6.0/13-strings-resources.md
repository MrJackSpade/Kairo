---
title: Move {{app}}'s own UI text into string resources
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system.

## Work

- Every string a player can see in `{{module}}/src/main/java` goes into `{{module}}/src/main/res/values/strings.xml` and is read through `UiText` or resources.
- Inserted values are format arguments (`%1$s`) and counts are `<plurals>`. Never build a sentence from pieces, since word order differs between languages.
- Above each string with arguments, a comment saying what each argument is. Translators see only the file.
- Text the shared frontend already has (Disk, Hard disk, Saves, Floppy disk) comes from the frontend's strings; only {{app}}'s own text goes here.
- Log lines, file names, keys, preference names and model identifiers stay in code.
- Run `python shared/tools/translations.py sync`. It creates the eight `values-<language>/strings.xml` copies, holding the English until translated.

## Done when

- No player-visible text is left in the Kotlin, and the English UI is unchanged.
- `translations.py check` finds no broken string.
- Under the `en-XA` pseudo-locale, every screen {{app}} adds shows accented text.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.
