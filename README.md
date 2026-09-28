# Kairo shared frontend

Kairo is the shared Android UI library for [Kairo98](https://github.com/MrJackSpade/Kairo98), a PC-98 emulator, and [KairoDos](https://github.com/MrJackSpade/KairoDos), a DOS emulator. It provides the game library and details, folder selection, navigation, session menu, controller editor, on-screen controls, keyboard presentation, and input routing. Each product pins a specific Kairo commit as its `shared/` submodule.

For installation, adding games, and setup in LaunchBox or ES-DE, use the [Kairo98 README](https://github.com/MrJackSpade/Kairo98#readme) or [KairoDos README](https://github.com/MrJackSpade/KairoDos#readme).

## Integration

The shared frontend owns `LibraryFlow`, `SessionFlow`, `FrontendNavigation`, `ControllerEditor`, and `ControllerProfileStore`. Each app supplies its media scanner, catalog resolver, guest keyboard specification, joystick targets, emulator action callbacks, display and audio bridge, product settings, package identity, and machine-specific assets. `ControllerGuestSpec` supplies console-specific values to the common editor.

Product repositories map their `:frontend` Gradle module to `shared/frontend`. A shared feature change is made here, then each product updates its pinned submodule commit. Emulator cores and catalog artwork remain in the product repositories.

The first-party frontend is [GPL-2.0-or-later](COPYING). See [licensing](docs/licensing.md) and [source provenance](docs/source-import.md) for bundled third-party materials.
