# Kairo frontend

Shared Android frontend code for Kairo98 and KairoDos. Product repositories pin a specific commit of this repository as `shared/` and map `:frontend` to `shared/frontend` in Gradle.

The first-party frontend code is GPL-2.0-or-later; see `COPYING`. The exact pinned frontend source is available from this repository. Product releases must also provide their own corresponding source and notices. The Spleen font has its own BSD-2-Clause notice in `third_party/spleen/LICENSE`.

The frontend contains the common library list, game detail page, Android document-tree walker, in-game session drawer, design widgets, and input helpers. Products implement the small library item and catalog interfaces and provide their own labels, settings, keyboards, game-file readers, emulator engines, package IDs, release workflows, and catalog assets.
