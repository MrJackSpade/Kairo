---
title: Choose the emulator core and verify its license
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Required. Every new system needs this; it is never closed as not planned.

## Applies when

Every new system.

## Work

- Compare the emulators that exist for {{system}}: accuracy, how much of the machine they cover (models, media, peripherals), speed on an ARM handheld, and whether they can run one frame at a time under a host.
- Read each candidate's license in its source, not only its README: the top-level license and every file with its own header.
- Acceptable: GPL-2.0-or-later, LGPL, BSD, MIT, zlib, public domain. Not acceptable: GPL-2.0-only (it cannot ship beside the GPL-3.0-only shader runtime, see Kairo `docs/licensing.md`), and non-commercial or ask-the-author terms (the Google Play build is paid).
- List every file under unacceptable terms, and whether it can be replaced with a first-party file or left out.
- If no candidate is acceptable, say so here and stop: the owner decides.

## Done when

- This description names the chosen core, its version and source, why it was chosen over the others, the license verdict, and the files to replace or leave out.

## Closing

- **Done:** write what was built (commits) and how it was verified into this description, then close as completed.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- KairoST chose Hatari (GPL-2.0-or-later) and replaced three GPL-2.0-only headers it carried with first-party files (`core/kairo/uae/`).
- fmgen and code under the pre-2016 MAME license were rejected for Kairo88 because of their terms.
