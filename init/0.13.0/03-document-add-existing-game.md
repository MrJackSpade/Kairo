---
title: Document and screenshot adding a game you already have
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Implement, when it applies. Build it only if **Applies when** holds for {{system}}.

## Applies when

Every new system, once {{ref:add-existing-game}} is closed with its steps.

## Work

- `docs/add-a-game.md`: the steps from {{ref:add-existing-game}}, for each kind of media {{system}} has. Add, find, firmware, test, configure and keep, in the order a player does them.
- A screenshot for each step, taken on the device from the release build, at native resolution, in `docs/screenshots/add-a-game/`.
- A link to it under the README's **Documentation** section (add that section if the README has none), so a player can be sent there.

## Done when

- The page shows every step with its screenshot, and the README links to it.

## Closing

- **Done:** write what was built (commits) and the device and build the screenshots came from into this description, then close as completed.
- **Does not apply:** write why into this description, then close as not planned.
