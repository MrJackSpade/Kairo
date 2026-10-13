# Interface

Kairo98 and KairoDos share one interface. Each app supplies its own settings and
game options; the structure, rows, motion, and controller behavior are the same.

## Layers

From bottom to top:

1. **Base**: the library, or the running game.
2. **Flyout**: `min(320 dp, screen − 40 dp)` from the left over a scrim. The library
   flyout opens from the menu button or Menu; the game flyout from a left-edge swipe,
   Back, Menu, Home, Mode, or the controller Menu action. Both share the SETTINGS list.
3. **Page stack** (`PageStack`): full-screen pages. Back pops one. In a game, Menu closes
   the whole stack and resumes.
4. **Tray**: opens under the row it belongs to, to confirm, choose a file, edit text,
   or listen for controller input.
5. **Confirmation modal** (`ConfirmPage`): leaving a game (Return to library or
   frontend, Exit) asks in a centred card over its own scrim, above the flyout.
6. **Message bar** (`Ui.message`): the only feedback channel. There are no system
   dialogs, system toasts, banners, or status-line messages.

While a game runs, any open page counts as the menu: the game pauses, and closing the
last page resumes it (`SessionFlow`).

## Structure

Library flyout: product, folder name, status · LIBRARY (Refresh, Manage library) ·
SETTINGS · Exit.

Game flyout: product, game title, status · GAME · SETTINGS · Restart game, Return to
library, Exit. Kairo98 also has Resume and Keep paused under the status; KairoDos has
neither, because the open flyout already pauses the game. Return reads **Return to frontend** when a
frontend launched the game. Kairo98's GAME group holds Save state, Load state, Disks,
and Game settings; KairoDos holds Game settings, because its core has no save states.

SETTINGS: Graphics, Controls, Machine, Configurations, Sound (a switch), About.
Kairo98's Machine page holds the base clock and firmware; KairoDos's holds the DOS CPU
speed, and its Graphics page adds the emulated video card and 3dfx rendering.

Configurations holds where the app keeps the user's files. Kairo98: SAVE STATES (Save folder);
KairoDos: SAVED GAMES (Save folder, for each game's overlay of written files). Save folder is
Private app storage or Custom folder. Changing it moves every save; a save in both places opens
Save conflicts, where the user keeps one copy of each before anything moves.

Manage library: SOURCE (folder, Refresh, Recheck all files), CATALOGS (Update catalogs,
Import catalog file…, Remove catalog), ARTWORK (Download missing images).

Controls: CONTROLLER (Controller layout, Button mapping, Controller inputs, Stick dead
zone), TOUCH SCREEN (Screen tap, Mouse touch), ON-SCREEN CONTROLS (Show on-screen
controls, D-pad style, Visible buttons, Arrange layout, Reset positions). Controls always
changes all games; a game's own mapping and touch choices are under Game settings.

Game settings, with the game's name in the scope chip: CONTROLS, MACHINE, LIBRARY
(Title, Box art, Screenshot), FILE (Location, ZIP entry, Content ID, Delete), and Reset
all game settings at the bottom.

## Rows

Every row is one line: the label on the left, the value right-aligned, and a 24 dp
trailing column that holds a chevron or nothing, so values line up. A row never has
subtext; explanations go in the group footnote. `SettingsRow` has one factory per kind:

| Kind | Behavior |
| --- | --- |
| `nav` | Opens a page. The value is a muted summary of that page. |
| `choice` | The value is the current choice. A opens a choice page; Left and Right cycle in place. |
| `switch` | Toggles in place. Left turns it off; Right turns it on. |
| `slider` | The live value stays in the value column. Left and Right step it. |
| `action` | Runs, asks first in a confirm tray, or opens its own tray. No chevron. Progress shows on the row. |
| `info` | Read-only, skipped by D-pad focus. Long values wrap. |
| `option` | Choice pages only, and the only row with a second line. Picking fills the radio, waits 150 ms, and returns. |

Value colors: Accent soft for a current value, muted for a summary, faint for nothing
set (Unassigned, None, Empty, Not installed), and Accent for a game's own value.

On per-game rows a source tag sits before the value: Shipped catalog, Updated catalog,
an installed catalog's name, User catalog, All games, Built-in, Filename, or Custom.
A choice's first option, Default, says where its value comes from, such as
"Shipped catalog: 2.5 MHz".

Danger red is used only for deleting files. Disabled rows stay focusable at 45%
opacity, and pressing one shows why it is disabled.

## Pages

A page header is a back arrow, a title that is the label of the row that opened the
page, and a scope chip that names what the page changes: All games, a game's name, or an
orientation and layout. Groups are a pixel-font label, a Surface card with 1 dp
dividers, and an optional footnote.

Only the button target page holds a draft, with Clear and Save in a fixed footer.
Every other change applies at once. Setup pages (first run and the required controller
layout) use the same rows with a step label when there are several steps, a title, a
description, and footer buttons; Menu does nothing there.

## Trays

A tray opens under its row in the selected fill. It holds a message, an optional text
field, and right-aligned buttons with the primary one last. Confirm buttons use the
verb (Restart, End game, Exit, Overwrite, Load, Delete, Reset, Remove), never OK or
Confirm. Confirmations open on Cancel; Cancel closes the tray and leaves the page open.
Left and Right move between buttons; A presses one; B, Up, or Down close the tray.

## Confirmation modal

Return to library (or frontend) and Exit confirm in a modal instead of a tray: a
`Confirm` with a `title` opens `ConfirmPage`. It fades in over the flyout in 150 ms,
which stays in place behind it. The card holds the title as a question ("Return to
library?", "Exit Kairo98?"), the message, then Cancel and the verb, right-aligned, focused
on Cancel. Left and Up focus Cancel; Right and Down focus the verb. A presses; B, Back,
or a tap outside the card cancels and returns to the flyout. Menu in a game closes the
modal with the rest of the stack.

In a Controller inputs tray, listening starts when the tray opens and captures every
controller button, B included. Only Android Back cancels. Keyboard keys still reach the
tray's buttons.

## Progress and notices

The **task strip** sits under the library header while a background task runs (scan,
recheck, catalog check, import, removal, image download, external launch) and names the
actual task, with Stop where the task can be cancelled. Results go to the message bar.

The **progress strip** covers the game area while a machine stops or starts.

A **notice** docks at the top of a running game for prompts that do not need to stop
it: an automatic disk swap failure, an unrecognized startup screen, or KairoDos's
installer ZIP removal. Its buttons take the D-pad and A or B while the menu is closed;
Menu still opens the flyout; B dismisses it.

## Motion

| Motion | Timing |
| --- | --- |
| Flyout open | 320 dp from the left in 200 ms; scrim fades in over 180 ms |
| Flyout close | 160 ms |
| Push a page | In from +48 dp with a fade; the page below moves to −48 dp and fades. 220 ms |
| Pop a page | The same, reversed. A flyout below slides back in |
| Choice picked | Pop after 150 ms; the row's value turns Accent for 900 ms |
| Switch | 150 ms |
| Message bar | Rises 8 dp and fades in over 150 ms, holds 2 s (3.5 s for long messages), fades out over 200 ms |

Entering motion uses `cubic-bezier(0.2, 0, 0, 1)` (`Ui.ease()`). Fades are linear.

## Controller

- Up and Down move between rows; info rows are skipped. Grids and keyboards move to the
  nearest item in the next row.
- Left and Right change the focused choice, slider, or switch, or move between tray buttons.
- A activates. B closes the tray, then the page, then the flyout.
- Menu closes everything and resumes in a game, and opens or closes the library flyout.
  On a game page in the library it opens Game settings.
- Focus starts on the first item, the current option on choice pages, Cancel in confirm
  trays and modals, and the current tab on the button target page. Pages keep their focused row and
  scroll position, so Back returns to the same place.
- Hats repeat after 400 ms, then every 120 ms. Menu navigation follows the user's
  Controller inputs mapping for A, B, and Menu; Escape is Back.
- Controller Restart and Exit actions open the game flyout with that row's confirm open:
  Restart's tray, or Exit's modal.

## Copy

Sentence case everywhere except pixel-font section labels. One word per state: On, Off,
Installed, Not installed, Built-in, Imported, Empty, Unassigned, None, Default.
Separators are " · " between values and " › " for containment. An ellipsis appears only
on actions that open the system picker. Every action that loses progress says
"Progress since your last save is lost."

## Code

| Piece | Where |
| --- | --- |
| Tokens, buttons, message bar | `Ui.kt` |
| Row model and specs | `SettingsRow.kt` |
| Row and tray views | `RowViews.kt` |
| Stack, page base, row pages | `PageStack.kt` |
| Groups, choice, flyout, confirmation, setup, document, image pages | `Pages.kt` |
| Task strip, progress strip, notices, Paused badge | `StatusViews.kt` |
| Library flyout and Manage library | `LibraryMenu.kt` |
| Controls, Button mapping, target page, Controller inputs | `ControllerPages.kt` |
| Per-game rows and source tags | `GameSettingsCoordinator.kt` |

Fixtures read pages through `Page.focusedItemId`, `itemIds()`, and `itemView()`, and
trays through `RowsPage.trayRowId`, `trayButtons()`, `trayFocused()`, and `trayMessage()`.
Modals are the top page as `ConfirmPage`, read through `title`, `message`, `buttons`,
and `focusedButton`.
