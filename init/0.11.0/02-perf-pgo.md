---
title: Perf: Profile-guided optimization
kind: performance
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Performance. Assess it for {{app}}'s core; change the code only where it applies and gains.

## Applies when

Always assessed. Whether the change is made is the assessment's result.

## Technique

Build an instrumented library, run representative games to record which branches and functions are hot, and compile the release library with that profile, so hot paths are laid out together and branches ordered by real counts. The profile is recorded on the RG DS and goes out of date with every change to the core (the build then fails until it is recorded again).

## Assess

- Whether the technique applies to {{app}}'s core, and whether the core already has it.
- The gain on the RG DS, measured with `game_profile` and the frame host's reports (frames a second against the guest's, run time per frame, late frames, audio underruns), on the same games and setup before and after.
- That the guest runs exactly as before: `game_profile ... check exact` hashes every picture and all sound of both builds.

## Closing

- **Done:** the change is in, the guest runs exactly as before (`game_profile ... check exact`), and the gain is measured on the RG DS. Write the numbers into this description, then close as completed.
- **Not done** (it does not apply to this core, the core already has it, it gains nothing measurable, or it is not taken up now): write what was found into this description, add the `deferred` label, and leave the ticket open.

## Examples (for reference)

From other Kairo apps, for reference only. {{app}}'s core and machine may need it done differently.

- `shared/cmake/KairoPgo.cmake`; KairoST's `core/pgo/` and `tools/perf/pgo-train.ps1`.
