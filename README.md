# Kairo frontend

Shared Android frontend code for Kairo98 and KairoDos. Product repositories pin a specific commit of this repository as `shared/` and map `:frontend` to `shared/frontend` in Gradle.

The first-party frontend code is GPL-2.0-or-later; see `COPYING`. The exact pinned frontend source is available from this repository. Product releases must also provide their own corresponding source and notices. The Spleen font has its own BSD-2-Clause notice in `third_party/spleen/LICENSE`.

The frontend owns folder selection, library restore and scanning flow, library and game detail screens, menu navigation and session lifecycle, controller editing and profile storage, on-screen controls, keyboard rendering, and input routing. `LibraryFlow`, `SessionFlow`, `FrontendNavigation`, `ControllerEditor`, and `ControllerProfileStore` are shared workflows; changes to these belong here so both products receive the same behavior when they update the submodule pin.

Each product supplies its media scanner and catalog, guest keyboard layout and key names, joystick targets, emulator action callbacks, display and audio bridge, product settings, package ID, release workflow, and catalog assets. Console-specific values enter the shared controller editor through `ControllerGuestSpec`. Product activities should wire these adapters into the shared workflows instead of copying frontend screens or menu logic.
