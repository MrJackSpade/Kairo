# Adding a new system

Everything a new Kairo app needs built and tested, from the way KairoST and Kairo64 were added
(Kairo commits `262a2e9` and `916e40b`, and those apps' histories). Each item is its own ticket in
the new app's repository (or in Kairo, for section 1).

## 0. Decisions

- [ ] **Emulator core.** Its license is verified from the source: GPL-2.0-or-later or permissive.
  Never GPL-2.0-only (it cannot ship beside the shader runtime, see [licensing](licensing.md)),
  fmgen, or code under the pre-2016 non-commercial MAME license. Files under other terms are
  listed for replacement (as Hatari's three FS-UAE headers were).
- [ ] **The real machine, for parity:** its models and regions (PAL/NTSC timing), CPU clocks,
  hard disks, every media type, peripherals (mouse, joystick ports, drives) and memory sizes.
  What the real machine had, the app supports.
- [ ] **Firmware:** the free replacement the app bundles as the default (EmuTOS, the Open ROMs,
  C-BIOS), and the player's own firmware as a per-game override, peripherals included.
- [ ] **Catalog identity:** the platform slug, the LaunchBox platform name, the MobyGames platform
  ID, and a new artwork code (permanent once a catalog using it is published).
- [ ] **App identity:** `Kairo<X>`, package `com.loxifi.kairo<x>`, module `kairo<x>`, the icon
  art and the brand accent color.
- [ ] **Game sets:** the standard sets the catalog scans, and a test set in the app's ignored
  `roms/` and `bios/` folders.

## 1. Registration in Kairo (one commit)

- [ ] `docs/templates/apps.json`: the app's entry (repo, system, module, package, googlePlay,
  firmware, saveStates, artworkHosts, privacyPage, problemDetail, frontends). It drives
  `tools/docs.py`, the family grid, `tools/tag-release.ps1` and `tools/uitest-all.ps1`.
- [ ] `tools/catalog_parts.py` `APPS`.
- [ ] `tools/launchbox.py` and `tools/mobygames.py` `PLATFORMS`.
- [ ] `tools/launchbox_artwork.py` `PLATFORM_FOLDERS` and `LaunchBoxArtworkReferences.kt`
  `PLATFORM_FOLDERS`, the same in both.
- [ ] `tools/audit_android_release.py` `FORBIDDEN`: the system's firmware file names and media
  extensions (the bundled free firmware keeps a name of its own).
- [ ] Docs: the platforms table in [catalog research](catalog-research.md), the app's own tests
  in [UI tests](ui-tests.md), the README family block (`tools/docs.py`).
- [ ] Any feature the system needs that the frontend lacks (a media kind, a `CatalogSetting`, a
  `DiskImageLibrary.Format` option, a firmware match) is built here, for every app.
- [ ] Every app pins the new commit.

## 2. Repository

- [ ] `MrJackSpade/Kairo<X>`: only the `origin` remote, `shared/` pinned as a submodule,
  `core.hooksPath shared/githooks`, author and committer MrJackSpade.
- [ ] `AGENTS.md` (core credited without being the brand, no proprietary firmware or games,
  GitHub and Play builds alike, every catalog setting a per-game setting), `README.md` with the
  `kairo:` blocks, `COPYING`, `LICENSE.md` and the privacy policy from `tools/docs.py`.
- [ ] Gradle: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, an executable
  `gradlew` delegating to shared.
- [ ] `.gitignore` (`bios/`, `roms/`, `.build/`, `.downloads/`), `.gitattributes`, and
  `third_party/.gitignore` re-including everything.
- [ ] `.github/workflows/`: `android.yml`, `tag-builds.yml`, `play-release.yml`,
  `discord-sync.yml`, `discord-release.yml`, calling Kairo at the pinned commit
  (`tools/check-workflow-pins.sh`); the signing secrets and Discord webhooks.
- [ ] `docs/development.md`, `docs/licensing.md`, `docs/source-import.md`, `docs/releases/`.

## 3. Third-party import

- [ ] A pinned snapshot in `third_party/`, its provenance in `docs/source-import.md` (URL, archive
  SHA-256, tag commit, date, exclusions, local changes).
- [ ] Files under incompatible terms replaced by first-party ones.
- [ ] Local changes in `tools/patches.py`, applied and checked by `tools/local_patches.py`.
- [ ] Generated sources committed, with their generator and a `--check` in `tools/checks.sh`.
- [ ] `tools/notices_sources.py` writing `THIRD_PARTY_NOTICES.txt`.
- [ ] The bundled free firmware staged into the app's assets by Gradle (KairoST's `stageEmutos`).

## 4. Native core (`core/`)

- [ ] `core/CMakeLists.txt` builds the emulator as a library: `-fsigned-char`, `-O3` in every
  build type, [KairoMachine.cmake](../cmake/KairoMachine.cmake) (hidden visibility), and
  [KairoPgo.cmake](../cmake/KairoPgo.cmake) once a profile is recorded.
- [ ] Game after game in one process: a fresh copy of the library for each machine (KairoST's
  `hatari_library.h`) or patches that reset the emulator's global state. A fatal error ends the
  session with its reason, never `exit()` or `abort()`.
- [ ] Headless video, sound, UI and main loop that hand each frame and its sound to the host.
- [ ] `<sys>_machine.cpp/.h`: the `FrameMachine` Core ([machine host](machine-host.md)):
  `run_frame`, `frame` or `gpu_picture`, `frame_seconds`, `audio_rate`, `read_audio`,
  `release_inputs`, `close`, `error`; `booting()` for the fast boot; keys, joysticks and mouse;
  media insert and eject per slot; save and load state; reset; the configuration (model, region,
  memory, clock, firmware, drives).
- [ ] The JNI bridge, `kairo<x>/src/main/cpp/<sys>_core.cpp` and its `CMakeLists.txt`, through
  `kairo_machine_library`, with `post` and `call`.
- [ ] `core/test/boot_test.cpp`: several media in one process, scripted presses, a state saved and
  loaded during a load, crashes and the last screen reported, frames kept; run with
  `tools/test-core.ps1` under `tools/run-limited.ps1`.
- [ ] `game_profile` with frame hashes, to measure speed and show two builds run the guest alike.

## 5. Android module (`kairo<x>/`)

- [ ] `build.gradle.kts`: `kairoProduct`, `kairoLabel`, `kairoCatalogRules`,
  `gradle/kairo-app.gradle`, and the app's own CMake targets and assets.
- [ ] `MainActivity : MachineActivity`: `identity`, `machine` (resolution, aspect, state file),
  `bridge`, `keyboard`, `guestKey`, `joystickControls`, `controllerSpec`, `bindingsCodec`, the
  `DiskImageLibrary.Format` (kinds, labels, size limits, programs), `mediaSlots`, `firmwareSets`
  (by name, SHA-1 or header, and size), `ownFirmwareInstalled`, `machineOptions`, `startGame`.
- [ ] `<Sys>Keys.kt`: the guest keyboard layout, the Android key mapping, labels, bindable keys
  and modifiers.
- [ ] `<Sys>Catalog.kt`: the `LayeredGameCatalog` schema (options, media roles, launch fields,
  `mediaExtensions`, update URLs).
- [ ] The firmware helper (Kairo64's `C64Firmware.kt`, KairoST's `StTos.kt`).
- [ ] `catalog/system-v1.json`: the controller mapping, every input mapped, the D-pad the arrow
  keys.
- [ ] `product_icon_art.png`, and `strings.xml` in English, ar, de, es, in, ja, ko, pt-rBR and ru;
  all text through `UiText`; games are never called ROMs.
- [ ] Unit tests: the keys and firmware tests, and the shared `CatalogSettingsTest`.

## 6. Catalog

- [ ] `tools/catalog_config.py`: `APP`, `PLATFORMS`, `ARTWORK_CODE`, `SUFFIXES`, `LIMITS`,
  `MEDIA`, `MOBYGAMES`, `SCANS`, `MACHINE_OPTIONS`, `MACHINE()`, `BROKEN_DISK`, `BOOT_KINDS`,
  `COMPANION`, `DISK_SWAPS`, `MEDIA_EXTENSIONS`, `LAUNCH_FIELDS`.
- [ ] Scans of the standard sets (`tools/scan_media.py`).
- [ ] The first catalog from LaunchBox alone: the snapshot, `research.py`, `app_catalog.py`,
  `fetch_artwork.py`, `art_review.py` ([catalog research](catalog-research.md)).
- [ ] The system's passes on the host core: the games that need the player's firmware, a drive,
  a model or region, or a start program, and the disks nothing starts (`BROKEN_DISK`), recorded in
  `catalog/research/`.
- [ ] Descriptions: no filler, main text clean, adult games' frank text in
  `descriptions-adult-v1.json`; all translated into the eight languages (`core-v4-<language>`).
- [ ] Adult artwork marked.
- [ ] The MobyGames dump and `research.py --mobygames`, before the first release that is not a
  test build.
- [ ] `tools/check-app.sh`, `audit_catalog_parts.py` and `audit_core_catalog.py` pass.

## 7. UI tests

- [ ] The `AppUnderTest` descriptor (biosFiles, bootGame, otherGame, defaultOption, searchTerm,
  arrowKeys, returnKey, spaceKey, mediaSwap).
- [ ] Every shared suite as `<App><Suite>Tests` (`StateTests` only with save states).
- [ ] The app's own `<Sys>Tests`.
- [ ] `uitest/resources.json` and the encrypted `TestResources.part1.dat` (`LOCAL_BUILD_TOKEN`).

## 8. On the device (RG DS)

- [ ] `tools/build.ps1 -Install`: a game boots and draws.
- [ ] Full speed (the AudioTrack frame count in `dumpsys media.audio_flinger`) with no
  underruns; a still screen costs little.
- [ ] Several launches in one process; a game after another; a game after a loaded state.
- [ ] Every media kind, disk sets, swaps and ejects, hard disks.
- [ ] The bundled firmware, the player's own, the first-run import.
- [ ] The controller defaults, touch keyboard and touchpad, two screens, launches from other
  frontends, save states, fast-forward, pause, Home and back.
- [ ] No unresourced text under the `en-XA` and `ar-XB` pseudo-locales.
- [ ] The license audit done; `audit_android_release.py` passes on the release build.

## 9. Release

- [ ] `docs/releases/v<version>.md`, `tools/tag-release.ps1`, the Play Console app with the Play
  publishing service account and the workload identity condition for its repository.
