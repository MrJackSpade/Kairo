<!-- kairo:discord -->
<p align="center">
  <a href="https://discord.gg/ZkYQ3bPTTq"><img src="https://raw.githubusercontent.com/MrJackSpade/Kairo/main/docs/assets/discord-banner.svg" alt="Join the Kairo Discord" width="49%"></a>
</p>
<!-- /kairo:discord -->

<!-- kairo:family -->
<h3 align="center">Kairo apps</h3>
<table align="center">
  <tr>
    <td align="center" width="33%"><a href="https://github.com/MrJackSpade/Kairo98"><img src="https://raw.githubusercontent.com/MrJackSpade/Kairo98/main/kairo98/src/main/res/drawable-nodpi/kairo98_icon_art.png" width="96" alt="Kairo98"><br><b>Kairo98</b></a><br>PC-98</td>
    <td align="center" width="33%"><a href="https://github.com/MrJackSpade/KairoDos"><img src="https://raw.githubusercontent.com/MrJackSpade/KairoDos/main/kairodos/src/main/res/drawable-nodpi/kairodos_icon_art.png" width="96" alt="KairoDos"><br><b>KairoDos</b></a><br>DOS</td>
    <td align="center" width="33%"><a href="https://github.com/MrJackSpade/Kairo88"><img src="https://raw.githubusercontent.com/MrJackSpade/Kairo88/main/kairo88/src/main/res/drawable-nodpi/product_icon_art.png" width="96" alt="Kairo88"><br><b>Kairo88</b></a><br>PC-88</td>
  </tr>
  <tr>
    <td align="center" width="33%"><a href="https://github.com/MrJackSpade/Kairo68"><img src="https://raw.githubusercontent.com/MrJackSpade/Kairo68/main/kairo68/src/main/res/drawable-nodpi/product_icon_art.png" width="96" alt="Kairo68"><br><b>Kairo68</b></a><br>X68000</td>
    <td align="center" width="33%"><a href="https://github.com/MrJackSpade/KairoTowns"><img src="https://raw.githubusercontent.com/MrJackSpade/KairoTowns/main/kairotowns/src/main/res/drawable-nodpi/product_icon_art.png" width="96" alt="KairoTowns"><br><b>KairoTowns</b></a><br>FM Towns</td>
    <td align="center" width="33%"><a href="https://github.com/MrJackSpade/KairoMSX"><img src="https://raw.githubusercontent.com/MrJackSpade/KairoMSX/main/kairomsx/src/main/res/drawable-nodpi/product_icon_art.png" width="96" alt="KairoMSX"><br><b>KairoMSX</b></a><br>MSX</td>
  </tr>
</table>
<!-- /kairo:family -->

# Kairo shared frontend

Kairo is the shared Android UI library for the [Kairo emulator apps](#kairo-apps). It provides the game library and game page, the library and game flyouts, the page stack with its settings pages, folder selection, controller mapping, on-screen controls, keyboard presentation, and input routing. [docs/interface.md](docs/interface.md) describes the interface; [docs/machine-host.md](docs/machine-host.md) describes the shared app and native host that frame-driven products build on. Each product pins a specific Kairo commit as its `shared/` submodule.

For installation and adding games, use each app's own README.

## Integration

The shared frontend owns `LibraryFlow`, `SessionFlow`, `FrontendNavigation`, `ControllerEditor`, and `ControllerProfileStore`. Each app supplies its media scanner, catalog resolver, guest keyboard specification, joystick targets, emulator action callbacks, display and audio bridge, product settings, package identity, and machine-specific assets. `ControllerGuestSpec` supplies console-specific values to the common editor.

Product repositories map their `:frontend` Gradle module to `shared/frontend`. A shared feature change is made here, then each product updates its pinned submodule commit. Emulator cores and catalog artwork remain in the product repositories.

The pinned shared project also owns the [Android build conventions and CI setup](docs/android-build.md). Product wrappers delegate to its Gradle wrapper; product workflows consume its local setup action.

The first-party frontend is [GPL-2.0-or-later](COPYING). See [licensing](docs/licensing.md) and [source provenance](docs/source-import.md) for bundled third-party materials.
