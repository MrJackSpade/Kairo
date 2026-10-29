# Adding a new system

Everything a new Kairo app needs built and tested, from the way KairoST and Kairo64 were added
(Kairo commits `262a2e9` and `916e40b`, and those apps' histories) and from every issue filed
against the Kairo apps. Each item is its own ticket in the new app's repository (or in Kairo, for
section 1). The issue after an item is where its lesson was learned.

## 0. Decisions

- [ ] **Emulator core.** Its license is verified from the source: GPL-2.0-or-later or permissive.
  Never GPL-2.0-only (it cannot ship beside the shader runtime, see [licensing](licensing.md)),
  fmgen, or code under the pre-2016 non-commercial MAME license. Files under other terms are
  listed for replacement (as Hatari's three FS-UAE headers were).
- [ ] **The real machine, for parity:** its models and regions (PAL/NTSC timing), CPU clocks,
  memory sizes, hard disks, every media type (tapes and cartridges too, Kairo88#2, Kairo64#50),
  peripherals (mouse, joystick ports, drives), its keyboards (international and JIS, with kana,
  KairoMSX#45, Kairo#81), its lock keys, a boot memory check, and a command line it boots to
  (BASIC, DOS). What the real machine had, the app supports.
- [ ] **Firmware:** the free replacement the app bundles as the default (EmuTOS, the Open ROMs,
  C-BIOS), and the player's own firmware as a per-game System override (Kairo98#107),
  peripherals included (hard disk controllers, KairoMSX#43).
- [ ] **Catalog identity:** the platform slug, the LaunchBox platform name, the MobyGames platform
  ID, and a new artwork code (permanent once a catalog using it is published).
- [ ] **App identity:** `Kairo<X>`, package `com.loxifi.kairo<x>`, module `kairo<x>`, the icon
  art and the brand accent color.
- [ ] **Game sets:** the standard sets the catalog scans, and a test set in the app's ignored
  `roms/` and `bios/` folders with one game of every media kind, a multi-disk game, a mouse game,
  a game that reads the cursor keys and the heaviest action game known.

## 1. Registration in Kairo (one commit)

- [ ] `docs/templates/apps.json`: the app's entry (repo, system, module, package, googlePlay,
  firmware, saveStates, artworkHosts, privacyPage, problemDetail, frontends). It drives
  `tools/docs.py`, the family grid, `tools/tag-release.ps1` and `tools/uitest-all.ps1`.
- [ ] `tools/catalog_parts.py` `APPS`.
- [ ] `tools/launchbox.py` and `tools/mobygames.py` `PLATFORMS`.
- [ ] `tools/launchbox_artwork.py` `PLATFORM_FOLDERS` and `LaunchBoxArtworkReferences.kt`
  `PLATFORM_FOLDERS`, the same in both. Artwork comes from LaunchBox references, never a
  self-hosted copy of LaunchBox's images (Kairo#36, KairoDos#85).
- [ ] `tools/audit_android_release.py` `FORBIDDEN`: the system's firmware file names and media
  extensions (the bundled free firmware keeps a name of its own).
- [ ] Docs: the platforms table in [catalog research](catalog-research.md), the app's own tests
  in [UI tests](ui-tests.md), the README family block (`tools/docs.py`).
- [ ] Any feature the system needs that the frontend lacks (a media kind, a `CatalogSetting`, a
  `DiskImageLibrary.Format` option, a firmware match, a `MachineCore` hook) is built here, for
  every app. Any feature another app has (startup commands, hard disks, clock, Skip memory check,
  disk swap automation) is the shared one, never a second copy (Kairo#83, KairoTowns#39).
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
- [ ] Version codes rise with every build, so a build installs over the last one (Kairo98#34).

## 3. Third-party import

- [ ] A pinned snapshot in `third_party/`, its provenance in `docs/source-import.md` (URL, archive
  SHA-256, tag commit, date, exclusions, local changes).
- [ ] Files under incompatible terms replaced by first-party ones.
- [ ] Every change to the snapshot is a patch in `tools/patches.py`, applied and checked by the
  shared `tools/local_patches.py`; nothing edited in the snapshot by hand (Kairo#92).
- [ ] Generated sources committed, with their generator and a `--check` in `tools/checks.sh`.
- [ ] `tools/notices_sources.py` for the shared notices generator, checked in CI (Kairo#86).
- [ ] Shared importers where one exists (the MAME subset importer, Kairo#93).
- [ ] The bundled free firmware staged into the app's assets by Gradle and installed with the
  shared shipped-asset install (Kairo#96).

## 4. Native core (`core/`)

### Build

- [ ] `core/CMakeLists.txt` builds the emulator as a library: `-fsigned-char`, `-O3` in every
  build type (KairoDos#51), [KairoMachine.cmake](../cmake/KairoMachine.cmake) (hidden visibility),
  and [KairoPgo.cmake](../cmake/KairoPgo.cmake) once a profile is recorded.
- [ ] Headless video, sound, UI and main loop that hand each frame and its sound to the host. The
  emulator's own menus and dialogs never show, not even after a guest crash (KairoDos#39).

### The machine

- [ ] `<sys>_machine.cpp/.h`: the `FrameMachine` Core ([machine host](machine-host.md)):
  `run_frame`, `frame` or `gpu_picture`, `frame_seconds`, `audio_rate`, `read_audio`,
  `release_inputs`, `close`, `error`; the configuration (model, region, memory, clock, firmware,
  drives, Skip memory check).
- [ ] The JNI bridge, `kairo<x>/src/main/cpp/<sys>_core.cpp` and its `CMakeLists.txt`, through
  `kairo_machine_library`, with `post` and `call`; state result codes from `machine_core.h`, not
  the core's own (Kairo#94).
- [ ] Game after game in one process: a fresh copy of the library for each machine (KairoST's
  `hatari_library.h`) or patches that reset the emulator's global state. Nothing carries into the
  next machine: boot state (KairoST#32), ROMs, which each machine loads itself even when the last
  one named the same files (Kairo64#11).
- [ ] A machine that stops for any reason (a fatal error, a CPU halt or JAM, the emulator
  quitting) ends the game with its reason and returns to the library; never `exit()`, `abort()`
  or a silent stop on the last frame (KairoST#7).
- [ ] The emulator's per-game settings and patches (Tsugaru's `-APP`) reach the machine, from a
  catalog setting (KairoTowns#77).
- [ ] Runs repeat exactly: a repeatable mode (fixed clock, fixed seed, synchronous media reads)
  and no uninitialized reads, so two runs of one build hash alike (KairoTowns#32).

### Picture and sound

- [ ] The core renders at the machine's own resolution and the GPU scales it; where the hardware
  was built from planes, palettes and sprites, the GPU composes it (Kairo#50).
- [ ] A frame's picture does not depend on where its drawing was split (Kairo68#33).
- [ ] Frame buffers outlive every copy the presenter thread makes of them (KairoTowns#7).
- [ ] Machine quirks that look like bugs are decided on purpose (Tsugaru's Trinitron damper
  line, KairoTowns#5).

### Host hooks

- [ ] `booting()` runs flat out while the guest loads from any medium, by its drive, tape or CD
  motor, not only during the first boot (Kairo#82, Kairo64#2).
- [ ] The time from Play to the first picture is short: the boot runs flat out and a bundled
  firmware's splash hold is cut (Kairo68#2, KairoST#5).
- [ ] Input telemetry (keyboard waits and polls, mouse reads) for the Auto touch mode (Kairo#78).
- [ ] Pointer warp for direct touch (Kairo#79).
- [ ] `locks()`: Caps and Kana read back from the emulator, and declared in the layout
  (Kairo#80).
- [ ] The prompt hooks of the shared startup typer (`prompt`, `typed_key`, `special_key`,
  `expand_command`) for every prompt the machine boots to (Kairo68#70, Kairo64#51).
- [ ] A command-line launch: the game's media inserted, nothing autostarted (Kairo#89).
- [ ] A media slot for every drive, tape deck and cartridge port, with insert, eject and
  `media_paths()` answered from the emulator's own state, so the Disks page is right after a
  state load (KairoTowns#76, Kairo98#103).
- [ ] Hard disk images mounted on the machine's own bus and booted from (KairoTowns#38).

### Input

- [ ] The emulated mouse is mapped to Kairo's mouse; the core fails when it is not (Kairo68#32).
- [ ] A key tap reaches the guest within half a second and is held long enough for the guest to
  read it (KairoST#3, KairoST#33).
- [ ] Inputs that share hardware lines are never sent together where the machine reads them as
  other inputs (the C64's joystick port 2 and keyboard matrix, Kairo64#12).

### Save states

- [ ] A state records which of the game's disks was in each drive and puts them back through
  `kairo_state_path` when it loads (Kairo98#94, Kairo68#69, Kairo64#49).
- [ ] A state round trip is exact: the machine a frame after the load matches the machine a frame
  after the save, every chip included (Kairo68#72).

### Host tools

- [ ] `core/test/boot_test.cpp`: several media in one process, scripted presses, a state saved and
  loaded during a load with its round trip checked, crashes and the last screen reported, frames
  kept; run with `tools/test-core.ps1` under `tools/run-limited.ps1`.
- [ ] `game_profile` with frame hashes (`check exact`), to measure speed and show two builds run
  the guest alike.

## 5. Android module (`kairo<x>/`)

- [ ] `build.gradle.kts`: `kairoProduct`, `kairoLabel`, `kairoCatalogRules`,
  `gradle/kairo-app.gradle`, and the app's own CMake targets and assets.
- [ ] `MainActivity : MachineActivity`: `identity`, `machine` (resolution, aspect, state file,
  mouse resolution), `bridge` (with the lock keys), `keyboard`, `guestKey`, `joystickControls`,
  `controllerSpec`, `bindingsCodec`, the `DiskImageLibrary.Format` (kinds, labels, size limits,
  programs), `mediaSlots`, `firmwareSets` (by name, SHA-1 or header, and size),
  `ownFirmwareInstalled`, `machineOptions`, `startGame`.
- [ ] Every machine setting is a `MachineOption`: the all-games default on the Machine page and a
  per-game override (Kairo98#105). Skip memory check (default on) where the boot counts memory
  (Kairo98#62); CPU clock where the machine had more than one (Kairo68#4); memory defaulting as
  high as games allow (KairoMSX#3).
- [ ] A game's other disks through the shared `companionFor` (Kairo#84); startup commands through
  the shared `StartupCommands` setting.
- [ ] `<Sys>Keys.kt`: the guest keyboard layout from the shared blocks where they fit
  (`JisMainBlock`, the shared symbol page, Kairo#49), legends following the running machine
  (KairoMSX#45), the Android key mapping, labels, bindable keys and modifiers.
- [ ] `<Sys>Catalog.kt`: the `LayeredGameCatalog` schema (options, media roles, launch fields,
  `mediaExtensions`, update URLs).
- [ ] The firmware helper (Kairo64's `C64Firmware.kt`, KairoST's `StTos.kt`).
- [ ] `catalog/system-v1.json`: the controller mapping, never Kotlin defaults. Every input mapped
  in both controller layouts, with and without sticks (Kairo#37); the D-pad the arrow keys and
  the joystick (KairoMSX#4); A Return and B Space, X and Y the joystick's buttons (KairoMSX#5,
  KairoTowns#4); the right stick the mouse.
- [ ] `product_icon_art.png`, and `strings.xml` in English, ar, de, es, in, ja, ko, pt-rBR and ru;
  all text through `UiText`; text every app has comes from the frontend's strings, not the app's
  (Kairo#95); no shared drawable copied (Kairo#97); the folder and empty-library texts name ZIP
  and 7z (Kairo98#109); games are never called ROMs.
- [ ] Unit tests: the keys and firmware tests, and the shared `CatalogSettingsTest`.

## 6. Catalog

- [ ] `tools/catalog_config.py`: `APP`, `PLATFORMS`, `ARTWORK_CODE`, `SUFFIXES`, `LIMITS`,
  `MEDIA`, `MOBYGAMES`, `SCANS`, `MACHINE_OPTIONS`, `MACHINE()`, `BROKEN_DISK`, `BOOT_KINDS`,
  `COMPANION`, `DISK_SWAPS`, `MEDIA_EXTENSIONS`, `LAUNCH_FIELDS`.
- [ ] Scans of the standard sets (`tools/scan_media.py`).
- [ ] The first catalog from LaunchBox alone: the snapshot, `research.py`, `app_catalog.py`,
  `fetch_artwork.py`, `art_review.py` ([catalog research](catalog-research.md)).
- [ ] **Disk sets.** Every multi-disk set in the standard sets is one library entry, with a record
  at least good enough to group it, and a check that fails on any ungrouped set (Kairo68#3).
- [ ] **Boot disks.** A pass reads each disk image and records how it starts; a data disk or a
  Disk 2 never leads a set (KairoST#36, KairoST#37). A disk that boots to a desktop or prompt gets
  the program or commands that start it (KairoST#34).
- [ ] **Launch pass.** Every library game booted on the host core on each setup until one runs
  it; the pass presses Start, Space and fire, plays past the title, and judges by the picture,
  not by sound (KairoST#6, KairoST#35, Kairo64#6). It records the firmware, drive, model and
  start program each game needs, and the dumps nothing starts (`BROKEN_DISK`), which never lead
  their set (KairoST#38).
- [ ] **Model pass.** Region and model decided by running the game on each, never by the region
  in its name (Kairo64#14), and machine settings from every source that states them (Kairo88#3).
- [ ] Names the generator indexes match the app's file-name lookup (names with dots, side and
  disk tags, Kairo#98).
- [ ] Descriptions for every game with a real source, written for the app, never filler
  (Kairo64#5, Kairo88#1); adult games' frank text in `descriptions-adult-v1.json` (Kairo68#71),
  main text clean.
- [ ] Japanese titles for a Japanese platform's games, from known sources only (Kairo#61).
- [ ] One ticket per language for the descriptions, the adult descriptions included and never
  left untranslated (Kairo#76).
- [ ] Adult artwork marked.
- [ ] The MobyGames dump and `research.py --mobygames`, with MobyGames' covers for games LaunchBox
  has none for, before the first release that is not a test build (Kairo64#7).
- [ ] `tools/check-app.sh`, `audit_catalog_parts.py` and `audit_core_catalog.py` pass.

## 7. UI tests

- [ ] The `AppUnderTest` descriptor (biosFiles, bootGame, otherGame, defaultOption, searchTerm,
  arrowKeys, returnKey, spaceKey, mediaSwap).
- [ ] Every shared suite as `<App><Suite>Tests` (`StateTests` only with save states), the disk
  swap with thousands of disks in the library (Kairo#48).
- [ ] The app's own `<Sys>Tests`: its firmware and System choices, its media kinds, its machine
  settings reaching the machine, the boot running faster than the real machine (Kairo68#5).
- [ ] `uitest/resources.json` and the encrypted `TestResources.part1.dat` (`LOCAL_BUILD_TOKEN`).
- [ ] The suite passes on the RG DS (Kairo64#9).

## 8. On the device (RG DS)

Each finding is its own ticket.

- [ ] `tools/build.ps1 -Install`: a game boots and draws.
- [ ] **Every media kind** played past its load, loose and in a ZIP and a 7z, each taking the
  controller, saving and loading a state, and a second game after it in the same process
  (Kairo64#8).
- [ ] **Speed.** The heaviest action games, not still screens, at the guest's frame rate with no
  audio underruns (KairoTowns#6); the flat-out speed during a load (Kairo64#1). A still screen
  costs little; crackle is underruns, found in the frame's work (Kairo68#30). Below full speed,
  open the performance tickets (Kairo68#6 to #28), JIT last.
- [ ] **Launches.** Play to first picture; several games in one process, a program after a disk
  and a game after a loaded state; a launch from other frontends (ES-DE, NeoStation, Daijisho)
  is the library's game, with its saves (Kairo98#63).
- [ ] **Controls.** Each D-pad direction and stick read alone by the guest (a BASIC `PEEK` loop,
  Kairo64#12); the cursor keys from the D-pad, the on-screen keyboard and a physical keyboard at
  the guest's prompt (Kairo64#15); every button, in both controller layouts; the bottom screen's
  Joystick tab shows the joystick bindings (Kairo#38).
- [ ] **Keyboard.** Taps register at once and are read (KairoST#3, KairoST#33); chorded keys
  light only their own key (Kairo64#3); Caps and Kana lights after a state load.
- [ ] **Mouse games.** The touchpad tab, direct touch, and Auto switching to Mouse
  (Kairo98#66, Kairo68#32).
- [ ] **Media.** A disk swap mid-game, the Disks page after a state load showing the state's
  disks, a state saved after a swap loading onto its own disk.
- [ ] **Firmware.** The bundled firmware, the player's own per game, the first-run import.
- [ ] **Library.** The full standard set on the SD card: a second start hashes nothing new and
  checks quickly, an app update rehashes nothing (Kairo#46), archives are read in place, not
  copied (Kairo#39), search responds while typing, the library opens without parsing the catalog
  on the UI thread (Kairo#52), the folder pickers work after a large games folder (Kairo#51).
- [ ] **Two screens.** Details and settings split between the screens, focus crosses them, tags
  as chips (Kairo#43, Kairo#53).
- [ ] No unresourced text under the `en-XA` and `ar-XB` pseudo-locales.
- [ ] The license audit done; `audit_android_release.py` passes on the release build.

## 9. Release

- [ ] `docs/releases/v<version>.md`, `tools/tag-release.ps1`, the Play Console app with the Play
  publishing service account and the workload identity condition for its repository.
- [ ] The Play listing translated into each language and added in Play Console, one ticket per
  language.
