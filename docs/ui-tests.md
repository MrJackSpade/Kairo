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
| `SearchTests` | Search finds the game and only matching games; on two screens, search is typed on the lower screen. |
| `GameTests` | The game starts and draws; Exit returns to the library; a saved state loads back. |
| `WordingTests` | No screen says "ROM" (setup steps, library, its menu, Machine, Manage library). |

Each app's own tests:

| App | Tests |
| --- | --- |
| Kairo88 (`Pc88Tests`) | A V1 mode game starts in V1 mode; a two-disk game starts with Disk B in drive 2; the main release starts, not its SR version. |
| Kairo68 (`X68000Tests`) | A two-disk game starts with its second disk in floppy 1; the boot runs faster than the real machine. |
| KairoTowns (`FmTownsTests`) | A CD game starts with its user disk in floppy A. |
| KairoMSX (`MsxTests`) | A turbo R game starts on the FS-A1GT; an MSX-BASIC cartridge starts on real firmware and draws; an MSX1 cartridge starts on C-BIOS MSX1; The Dam Busters starts from the Start button. |
| Kairo98 (`Pc98Tests`) | The built-in font stands in until one is imported. |
| KairoDos (`DosTests`) | An eXoDOS installer installs and runs on its first launch; the ARM64 dynamic core runs generated code. |

## Running

```
shared\tools\uitest.ps1 -Device <serial>
shared\tools\uitest.ps1 -Device <serial> -Class com.mrjackspade.kairo88.uitest.Pc88Tests
```
