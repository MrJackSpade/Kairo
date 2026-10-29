# Adding a new system

Everything a new Kairo app needs built, checked, assessed or translated. Each line is a ticket in
`init/<version>/`, and each version is a milestone, in version order. A ticket's body says when it
applies, what kind it is, what to do and how it closes, so not every ticket means work: a
validation builds nothing unless it fails, and a performance ticket is done only where it gains.

`tools/init_system.py` files them in the new app's repository (this file is written from them by
`init_system.py --checklist`; edit the tickets, not this file):

```
python shared/tools/init_system.py --repo MrJackSpade/<App> --app <App> --system "<System>" --dry-run
python shared/tools/init_system.py --repo MrJackSpade/<App> --app <App> --system "<System>"
```


## 0.1.0

- [ ] Choose the emulator core and verify its license (implement)
- [ ] Survey the real <System> for parity (implement)
- [ ] Firmware: a free default the app ships, and the player's own (implement)
- [ ] Catalog identity: platform slug, LaunchBox, MobyGames and artwork code (implement)
- [ ] App identity: name, package, module, icon and accent color (implement)
- [ ] Standard game sets and the test set (implement)

## 0.2.0

- [ ] Register <App> in Kairo's apps.json (implement)
- [ ] Register <App> in Kairo's catalog tools (implement)
- [ ] Add <System> to Kairo's LaunchBox and MobyGames tools (implement)
- [ ] Add <App>'s artwork code to both platform folder tables (implement)
- [ ] Forbid <System> firmware and games in the release audit (implement)
- [ ] List <App> in Kairo's documentation (implement)
- [ ] Build in Kairo what <App> needs from the shared frontend (implement)
- [ ] Pin the Kairo commit that registers <App> in every app (implement)

## 0.3.0

- [ ] Create the <App> repository (implement)
- [ ] AGENTS.md, README, licenses and privacy policy (implement)
- [ ] The Gradle project on the shared conventions (implement)
- [ ] Ignore and attributes files (implement)
- [ ] Workflows, secrets and webhooks (implement)
- [ ] The docs folder (implement)
- [ ] Each build installs over the one before (validation)

## 0.4.0

- [ ] Import the core as a pinned snapshot (implement)
- [ ] Replace the files under terms the app can't ship (implement)
- [ ] Keep every change to the snapshots as a checked patch (implement)
- [ ] Commit the core's generated sources with their generator (implement)
- [ ] Third-party notices from the shared generator (implement)
- [ ] Import through the shared importer (implement)
- [ ] Ship the free firmware and install it with the shared helper (implement)

## 0.5.0

- [ ] Build the core as a library with the Kairo build settings (implement)
- [ ] Run the core headless, with none of its own UI (implement)
- [ ] The machine: the frame host's Core and its configuration (implement)
- [ ] The JNI bridge on the shared machine host (implement)
- [ ] Run game after game in one process, with nothing carried over (implement)
- [ ] A machine that stops ends the game with its reason (implement)
- [ ] The core's per-game options reach the machine (implement)
- [ ] Two runs of one build are identical (implement)
- [ ] Render at the machine's resolution and scale on the GPU (implement)
- [ ] A frame's picture does not depend on how its drawing is split (validation)
- [ ] Frame buffers stay valid while the presenter copies them (validation)
- [ ] The core's display artefacts are chosen on purpose (validation)
- [ ] Run flat out while the guest loads from its media (implement)
- [ ] Play reaches the game's first picture quickly (validation)
- [ ] Report what the guest reads, for the Auto touch mode (implement)
- [ ] Move the guest's pointer to a touched point (implement)
- [ ] Report the lock keys from the core (implement)
- [ ] Prompt hooks for typed startup commands (implement)
- [ ] Open a game at the guest's command line (implement)
- [ ] A media slot for every drive, tape deck and cartridge port (implement)
- [ ] Hard disk images (implement)
- [ ] Connect the emulated mouse to Kairo's mouse (implement)
- [ ] A key tap reaches the guest at once and is read (validation)
- [ ] No binding sends inputs the machine can't tell apart (validation)
- [ ] A save state restores the disks it was saved with (implement)
- [ ] A save state loads back exactly (validation)
- [ ] The host boot test (implement)
- [ ] game_profile: speed and exactness on host and device (implement)

## 0.6.0

- [ ] The app module's build file (implement)
- [ ] MainActivity on the shared MachineActivity (implement)
- [ ] Every machine setting is a machine option with a per-game override (implement)
- [ ] Skip memory check, on by default (implement)
- [ ] CPU clock setting (implement)
- [ ] Memory defaults as high as games allow (implement)
- [ ] Find a game's other disks with the shared lookup (implement)
- [ ] The startup commands setting (implement)
- [ ] The guest keyboard layout (implement)
- [ ] The catalog schema (implement)
- [ ] The firmware chooser (implement)
- [ ] The default controller mapping (implement)
- [ ] Move <App>'s own UI text into string resources (implement)
- [ ] The app's resources: nothing duplicated, the archive wording right (validation)
- [ ] Unit tests (implement)

## 0.7.0

- [ ] The catalog configuration (implement)
- [ ] Scan the standard sets (implement)
- [ ] The first catalog, from LaunchBox (implement)
- [ ] Every multi-disk set is one library entry (implement)
- [ ] Detect how each disk starts (implement)
- [ ] Launch pass: boot every game on the host core (implement)
- [ ] Decide each game's model and region by running it (implement)
- [ ] Machine settings the sources state (implement)
- [ ] The catalog's name index matches the app's file name lookup (validation)
- [ ] Original descriptions for every game (implement)
- [ ] Adult descriptions for the adult games (implement)
- [ ] Mark the adult artwork (implement)
- [ ] MobyGames in the catalog (implement)
- [ ] The catalog audits pass (validation)

## 0.8.0

- [ ] The UI test descriptor (implement)
- [ ] Every shared UI test suite (implement)
- [ ] <App>'s own UI tests (implement)
- [ ] UI test resources (implement)
- [ ] The UI tests pass on the RG DS (validation)

## 0.9.0

- [ ] A game boots and draws on the device (validation)
- [ ] Every media kind plays past its load (validation)
- [ ] Full speed on the heaviest games (validation)
- [ ] Starting games: first picture, several in a row, other frontends (validation)
- [ ] Every control reaches the guest as itself (validation)
- [ ] The on-screen keyboard (validation)
- [ ] Mouse games (validation)
- [ ] Media swaps and save states (validation)
- [ ] Firmware on the device (validation)
- [ ] The library at full size (validation)
- [ ] Two screens (validation)
- [ ] Sets that ship a hard disk or CMOS reach their game (validation)
- [ ] A game whose files the player changed still starts (validation)
- [ ] A file a patched game needs loads for that game (validation)
- [ ] No text outside the string resources (validation)
- [ ] License and release audits (validation)

## 0.10.0

- [ ] The library (validation)
- [ ] Search (validation)
- [ ] Startup (validation)
- [ ] Artwork (validation)
- [ ] Catalog updates (validation)
- [ ] Navigation (validation)
- [ ] Settings pages (validation)
- [ ] The controller (validation)
- [ ] On-screen controls (validation)
- [ ] The screen (validation)
- [ ] Sound under input (validation)
- [ ] Two-screen details (validation)
- [ ] Saves (validation)
- [ ] Library contents (validation)
- [ ] About (validation)

## 0.11.0

- [ ] Perf: Profiling and measurement harness (performance)
- [ ] Perf: Profile-guided optimization (performance)
- [ ] Perf: Link-time optimization (ThinLTO) (performance)
- [ ] Perf: Symbol binding and visibility (performance)
- [ ] Perf: Compiler optimization level and target flags (performance)
- [ ] Perf: Work done on every entry into the CPU core (performance)
- [ ] Perf: CPU timeslice fragmentation (performance)
- [ ] Perf: Idle and wait-loop skipping (performance)
- [ ] Perf: Instruction fetch and register access (performance)
- [ ] Perf: Guest memory access (performance)
- [ ] Perf: Condition-flag evaluation (performance)
- [ ] Perf: Graphics chip fast paths (performance)
- [ ] Perf: Video rendering dirty tracking (performance)
- [ ] Perf: GPU composition of the guest picture (performance)
- [ ] Perf: Frame copies and pixel conversion (performance)
- [ ] Perf: Presentation off the emulation thread (performance)
- [ ] Perf: Sound synthesis off the emulation thread (performance)
- [ ] Perf: Audio resampler SIMD (performance)
- [ ] Perf: Frame pacing on a fixed grid (performance)
- [ ] Perf: Audio output buffering and latency (performance)
- [ ] Perf: Emulation thread priority (performance)
- [ ] Perf: Guest CPU cycle budget (performance)

## 0.12.0

- [ ] Japanese: translate <App>'s app strings (translation)
- [ ] Portuguese (Brazil): translate <App>'s app strings (translation)
- [ ] Spanish: translate <App>'s app strings (translation)
- [ ] Korean: translate <App>'s app strings (translation)
- [ ] Indonesian: translate <App>'s app strings (translation)
- [ ] Arabic: translate <App>'s app strings (translation)
- [ ] Russian: translate <App>'s app strings (translation)
- [ ] German: translate <App>'s app strings (translation)
- [ ] Japanese: translate <App>'s catalog descriptions (translation)
- [ ] Portuguese (Brazil): translate <App>'s catalog descriptions (translation)
- [ ] Spanish: translate <App>'s catalog descriptions (translation)
- [ ] Korean: translate <App>'s catalog descriptions (translation)
- [ ] Indonesian: translate <App>'s catalog descriptions (translation)
- [ ] Arabic: translate <App>'s catalog descriptions (translation)
- [ ] Russian: translate <App>'s catalog descriptions (translation)
- [ ] German: translate <App>'s catalog descriptions (translation)
- [ ] Japanese: original Japanese titles in <App>'s catalog (translation)

## 0.13.0

- [ ] A player adds, tests and configures a game they already have (validation)
- [ ] A player creates a new game on a blank disk (implement)
- [ ] Document and screenshot adding a game you already have (implement)
- [ ] Document and screenshot creating a new game (implement)

## 1.0.0

- [ ] Release <App> (implement)
- [ ] <App>'s Play listing (implement)
- [ ] Japanese: translate <App>'s Play listing (translation)
- [ ] Portuguese (Brazil): translate <App>'s Play listing (translation)
- [ ] Spanish: translate <App>'s Play listing (translation)
- [ ] Korean: translate <App>'s Play listing (translation)
- [ ] Indonesian: translate <App>'s Play listing (translation)
- [ ] Arabic: translate <App>'s Play listing (translation)
- [ ] Russian: translate <App>'s Play listing (translation)
- [ ] German: translate <App>'s Play listing (translation)
- [ ] Japanese: add <App>'s Play listing in Play Console (translation)
- [ ] Portuguese (Brazil): add <App>'s Play listing in Play Console (translation)
- [ ] Spanish: add <App>'s Play listing in Play Console (translation)
- [ ] Korean: add <App>'s Play listing in Play Console (translation)
- [ ] Indonesian: add <App>'s Play listing in Play Console (translation)
- [ ] Arabic: add <App>'s Play listing in Play Console (translation)
- [ ] Russian: add <App>'s Play listing in Play Console (translation)
- [ ] German: add <App>'s Play listing in Play Console (translation)
