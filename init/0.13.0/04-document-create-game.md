---
title: Document and screenshot creating a new game
kind: implement
---
Part of {{app}}'s new-system tickets (Kairo `docs/new-system.md`, `init/`). Write findings, decisions and results into this description, not into comments.

**Kind:** Implement, when it applies. Build it only if **Applies when** holds for {{system}}.

## Applies when

{{ref:create-game}} is closed as completed with its steps.

## Work

- `docs/create-a-game.md`: the steps from {{ref:create-game}}. Create the blank disk, mount it with the installer media, prepare it, install, and save, in the order a player does them.
- A screenshot for each step, taken on the device from the release build, at native resolution, in `docs/screenshots/create-a-game/`.
- A link to it under the README's **Documentation** section (add that section if the README has none), so a player can be sent there.

## Done when

- The page shows every step with its screenshot, and the README links to it.

## Closing

- **Done:** write what was built (commits) and the device and build the screenshots came from into this description, then close as completed.
- **Does not apply:** write why into this description (the create ticket did not apply), then close as not planned.
