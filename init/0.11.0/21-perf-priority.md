---
title: Perf: Emulation thread priority and scheduling
kind: performance
---
From Kairo's new-system checklist (`docs/new-system.md`, `init/`). Findings, decisions and results go into this description, not comments.

**Kind:** Performance: assess it for {{app}}'s core, and make the change only where it applies and gains.

## Applies when

Always assessed. Whether it is done is the assessment's result.

## Technique

Give the emulation thread urgent-display standing (`setpriority(PRIO_PROCESS, 0, -16)`), so the UI and background work don't take its slices.

## Assess

- Whether the technique applies to {{app}}'s core, and whether the core already has it.
- What it gains on the RG DS, measured with `game_profile` and the frame host's reports (frames a second against the guest's, run time per frame, late frames, audio underruns), on the same games and setup before and after.
- That the guest runs exactly as before: `game_profile ... check exact` hashes every picture and all sound.

## Closing

- **Done:** the change is in, the guest runs exactly as before (`game_profile ... check exact`), and the gain is measured on the RG DS: record the numbers in this description, then close as completed.
- **Not done** (it does not apply to the core, the core already has it, it gains nothing measurable, or it is not taken up now): record what was found in this description, add the `deferred` label and leave the ticket open.

## Learned in

`Kairo68#26`
