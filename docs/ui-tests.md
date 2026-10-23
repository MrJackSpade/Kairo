# UI tests

Every Kairo app has device tests in the style of Selenium tests: the app names its elements, the
tests select them by those names, act on them as a player would, and assert what the player would
see. They run on a real device, in a build that installs beside any other copy of the app.

## Structure

| Where | What |
| --- | --- |
| `frontend` (`TestAttributes.kt`) | The element names (test id and key), the UI test document folder, and `GameProbe`. |
| `uitest/` | The framework (`Driver`, `By`, `Element`, `Screens`, `Pictures`, `TestLog`, `Expect`, `Patience`, `TestDocuments`) and the shared suites (`suites/`). |
| `uitest/provider/` | The document provider compiled into UI test builds only. |
| `gradle/kairo-uitest.gradle` | The `uiTest` build type, the orchestrator, and the test resources. |
| `tools/uitest.ps1` | Builds, installs, runs and pulls the logs. |
| `tools/test_resources.py` | Packs and unpacks the encrypted test resources. |
| `<app>/src/androidTest/.../uitest/` | The app's descriptor (`AppUnderTest`), its shared suites, and its own tests. |
| `<app repo>/uitest/` | `resources.json` (what the bundle holds) and `TestResources.part1.dat` (the bundle). |

## Element names

A view carries a test id, and a key when it stands for data (`TestAttributes.mark`). Ids are unique
within their page or row; tests select an element inside its page.

| Element | Id | Key |
| --- | --- | --- |
| Any page | `page` | its title, or `flyout`, `modal`, `Game`, `Save state`, `Load state` |
| Any focusable item | its navigation id: a row id, `footer:<id>`, `play`, `settings`, `slot<n>` | |
| Settings row parts | `label`, `value`, `tag`, `description` | a switch or option row: `true`/`false` |
| Tray | `tray.input`, `tray.button` | the button's label |
| Modal button | `modal.button` | the button's label |
| Page | `page.back`, `page.scroll` | |
| Library | `library.brand`, `library.menu`, `library.search`, `library.search.field`, `library.list`, `library.scanning`, `library.status`, `library.choose-folder` | |
| Library entry | `library.entry`, with `title` and `detail` inside | the entry id |
| Game | `game.surface` | |
| Lower screen | `secondary.library`, `secondary.search.field`, `secondary.surface` | |
| On-screen keyboard key | `keyboard.key` | the guest key code |
| Keyboard panel | `keyboard.tab`, `keyboard.touchpad`, `keyboard.swap`, `keyboard.close`, `keyboard.mapping` | a tab's page number |
| On-screen control | `touch.control` (`touch.arrange` while arranging) | the control: `a`, `start`, `up`, `ls`, … |
| Button mapping key picker | `target.key` (page `Button target`) | the guest key code |

Every focusable item also carries a role (`TestAttributes.item`): its navigation id, whatever its
test id. `By.item(id)` and `By.anyItem()` select by it; the menu crawler finds every item this way.

A new element gets a name where it is built (`.testId("…")`); pages and their focusable items are
named automatically (`Page.attach`, `Page.renav`).

## How the tests act

- **Selecting**: `By.id`, `By.key`, `By.page`, `By.text`, `By.idMatching`, combined with `and`,
  `withKey`, `withText`. `driver.find(by)` and `element.find(by)` give an `Element`, which holds a
  selector path, never a view: every action looks it up again, so it cannot go stale.
- **Touching**: `element.tap()` reads the element's position in the same main-thread turn that sends
  the touch, so the coordinates are never out of date. It scrolls the element into view first when
  its page has scrolled it away; `revealIn` brings a library row into a list that has no view for it
  yet.
- **Keys**: `driver.press(keyCode)` sends a controller (or keyboard) key through the system's input
  path to whichever window has focus; `driver.type` types text.
- **Gestures**: `element.hold(millis)` and `element.drag(from, to)` touch on-screen controls, the
  touchpad and sliders, reading the element's position again for every event.
- **What the game receives**: the app records every key, joystick control, mouse motion and button
  sent to the guest (`GuestInputRecord`, `KairoActivity.guestInput`). `driver.inputMark()` notes
  where it stands; `inputSince` and `waitForInput` read what arrived after.
- **Launches**: `driver.launchExternally(uri)` starts a game as a frontend does (a VIEW intent).
- **Every menu**: `MenuCrawler` opens every item and page reachable from the page on top, closing
  trays and modals with Cancel, restoring switches, answering pickers and links as cancelled, and
  skipping what is destructive or slow; it checks each page and reports what it reached.
- **Pickers**: the tests never drive the system file picker. `TestDocuments.answerFolder` and
  `answerFile` answer the app's picker request with a folder or file of the test resources, served
  by the UI test build's document provider. The app treats its own provider like any other
  (`FolderGrants`).
- **The game**: `driver.probe()` reads the session (`KairoActivity.gameProbe`): its state, frames,
  media by slot (`media.<slot>`), options (`option.<id>`) and what the machine started with
  (`machine.<name>`). `driver.pictures.game()` copies the game's picture as shown.
- **Waits** poll until their condition holds. They have no bound unless the run sets
  `patienceSeconds` (`uitest.ps1 -PatienceSeconds`); every 10 seconds a wait logs what it still waits for.

## The record

Each test writes `log.jsonl` (every step, wait, probe and assertion, with what it found),
screenshots of every window on every screen, and game pictures, through the AndroidX test storage
service; a failed test adds a screenshot. `uitest.ps1` pulls them to `.build\uitest\<run id>\`.

## Isolation

The `uiTest` build type is a debug build with its own package (`<applicationId>.uitest`) and label
("Kairo88 UI test"), so it installs beside a release or Play copy without touching its data. It has
no update check. The tests run under Android Test Orchestrator with `clearPackageData`, so every test
starts at first run; a test that finds an earlier test's documents fails at once.

## Test resources

The games and BIOS files the tests use are copyrighted, so each app keeps them only encrypted:
`uitest/TestResources.part1.dat`, AES-256-GCM keyed from the `LOCAL_BUILD_TOKEN` user environment
variable. The build unpacks them into the test APK's assets when they are not unpacked yet; a
build without the variable fails there. `uitest/resources.json` lists what the bundle holds and where
each file comes from on this machine; `tools/test_resources.py pack <repo>` rebuilds the bundle.

## The tests

Shared suites run in every app through its descriptor:

| Suite | Tests |
| --- | --- |
| `FirstRunTests` | The first step asks for the games folder; the controller step gives no instructions; choosing the folder lists its games; the BIOS step lists every file with Required or Optional and a description; importing the BIOS folder installs every file. |
| `LibraryTests` | The header shows the product name; each game is one entry; different releases stay separate; non-games are never listed; no screen tells the player how to open a game. |
| `SearchTests` | Search finds the game and only matching games; closing search brings back every game; a search with no match shows none; on two screens, search is typed on the lower screen. |
| `GameTests` | The game starts, draws and is named; Return to library ends it; Exit closes the app; Restart boots it again; the menu pauses it. |
| `WordingTests` | No screen says "ROM": the setup steps, the library, a game's page (the crawls check every other page). |
| `MenuTests` | Every page and item of the library menu, the game menu, and a game's settings opens and returns (MenuCrawler), with plain words on each. |
| `ControllerTests` | Every mapped button reaches the game and is released; mapping a button to a key sends that key; a game's own mapping stays with it; Reset restores the defaults; a fast-forward button speeds the game while held; the controller drives the menus. |
| `TouchTests` | On-screen buttons and the D-pad reach the game; hiding the on-screen controls removes them; the touch keyboard types its keys; the touchpad moves and clicks the mouse; a tap on the game in Mouse mode clicks. |
| `LaunchTests` | A frontend launch starts the game, also before setup; returning from it closes the app; a second launch replaces the first. |
| `StateTests` | A saved state loads back to where it was saved; each slot keeps its own; a state outlives the app; a game's states are its own. |
| `MediaTests` | Inserting a disk puts it in the drive; ejecting empties it. |
| `LibraryManagementTests` | Refresh finds a game added to the folder; deleting a game removes its file; resetting a game's settings restores the defaults; saves go to a chosen folder. |
| `SettingsTests` | A machine default applies to games; a game's own setting overrides it; a setting is kept when the app starts again; Sound switches off and on. |

Each app's own tests:

| App | Tests |
| --- | --- |
| Kairo88 (`Pc88Tests`) | A V1 mode game starts in V1 mode; a disk's own mode outranks the default; a game without one starts in the default; a two-disk game starts with Disk B in drive 2; the main release starts, not its SR version. |
| Kairo68 (`X68000Tests`) | A two-disk game starts with its second disk in floppy 1; the boot runs faster than the real machine; a key press ends the fast boot; the memory setting reaches the machine. |
| KairoTowns (`FmTownsTests`) | A CD game starts with its user disk in floppy A; the CPU clock and memory settings reach the machine. |
| KairoMSX (`MsxTests`) | A turbo R game starts on the FS-A1GT; its kanji font is found by content; an MSX-BASIC cartridge starts on real firmware and draws; an MSX1 cartridge starts on C-BIOS MSX1; a game's own machine choice wins; The Dam Busters starts from the Start button. |
| Kairo98 (`Pc98Tests`) | The Disks page shows each drive; ejecting a floppy empties it; the built-in font stands in until one is imported. |
| KairoDos (`DosTests`) | An eXoDOS installer installs and runs on its first launch; the ARM64 dynamic core runs generated code; the video card choice is kept. |

KairoDos has no saved states, so it runs no `StateTests`; Kairo98 and KairoDos have no shared media
page (Kairo98's Disks page has its own tests).

## Running

```
shared\tools\uitest.ps1 -Device <serial>
shared\tools\uitest.ps1 -Device <serial> -Class com.mrjackspade.kairo88.uitest.Pc88Tests
```
