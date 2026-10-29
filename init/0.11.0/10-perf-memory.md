---
title: Perf: Guest memory access
kind: performance
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Performance. Assess it for {{app}}'s core; change the code only where it applies and gains.

## Applies when

Always assessed. Whether the change is made is the assessment's result.

## Technique

Every guest load and store takes this path. Resolve ordinary RAM to a host pointer with as few checks and calls as possible, and send hot non-RAM regions (video RAM) straight to their handler instead of through general range tests.

## Assess

- Whether the technique applies to {{app}}'s core, and whether the core already has it.
- The gain on the RG DS, measured with `game_profile` and the frame host's reports (frames a second against the guest's, run time per frame, late frames, audio underruns), on the same games and setup before and after.
- That the guest runs exactly as before: `game_profile ... check exact` hashes every picture and all sound of both builds.

## Closing

- **Done:** the change is in, the guest runs exactly as before (`game_profile ... check exact`), and the gain is measured on the RG DS. Write the numbers into this description, then close as completed.
- **Not done** (it does not apply to this core, the core already has it, it gains nothing measurable, or it is not taken up now): write what was found into this description, add the `deferred` label, and leave the ticket open.
