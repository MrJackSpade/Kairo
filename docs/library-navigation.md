# Held library navigation (Kairo #2)

## Reproduction and fix

The RGDS device fixture reproduced a settled off-screen selection before the
fix: twelve Down movements followed by twelve Up movements before layout left
game 0 at y=-22..58 in a 402-pixel list viewport. The assertion still failed
after allowing the pending traversal to settle.

Two related errors were involved:

- Scroll requests used row coordinates from before the previous request had
  been applied. A reversal could leave the last pending request inconsistent
  with the current selected index.
- Offsets passed to `setSelectionFromTop` counted top padding twice. Android
  adds list padding to that offset internally, so bottom alignment could still
  leave the selected row clipped. See [Android AbsListView source](https://android.googlesource.com/platform/frameworks/base/%2B/android-13.0.0_r4/core/java/android/widget/AbsListView.java).

Shared LibraryScreen now checks the latest selected game row after layout and
before drawing a requested navigation update. If a scroll still needs applying,
that stale frame is deferred; the next layout is checked again. The flag clears
once the row is visible, so ordinary touch scrolling is not continuously pulled
back to the controller selection. Filtering and returning to the library also
request this check. Section headers are not used as the selected game row.
Rows taller than the available viewport are top-aligned.

## Regression coverage

The shared Android fixture checks:

- rapid forward/reverse events before layout;
- 40 repeated D-pad Down events followed by 40 Up events, across multiple
  viewports and both list boundaries;
- actual shared hat-axis repeat scheduling in both directions;
- the pinned Continue row and section headers;
- navigation through filtered results and restoration of the full list.

An OnDrawListener inspects every drawn test frame for exactly one activated
row fully inside the padded viewport. Test assertions are returned to the
instrumentation thread rather than crashing the app's main thread.

The fixture also retains the #1 metadata/recycling, artwork-space, and
asynchronous-description regression checks.

## RGDS results, October 2, 2026

The complete shared fixture passed in both installed applications. Signed APKs
updated the existing packages, and their installed hashes matched the local
tested builds:

- DOS: `218d31e6444d57224b1878cd0a34c9dee50cc05801f4f91fc9bc8a836826f65f`
- PC98: `21eccf6c314a5262b7067ef2c71a8b693c582b8bb8639c21b9e169778d9f810c`

Real library input was also checked in each app with ten Down long-press
commands on display 2. The selected row remained fully visible at the bottom
of the viewport in both captured screenshots.

## Library flyout focus, Kairo #20

The baseline regression reproduced the reported stuck Menu highlight on the
RGDS: after a touch-opened flyout was closed, the Menu button had `focused=true`
while selected, pressed and activated were false. The disappearing focused
drawer caused Android to focus the first header button; its focus drawable
then appeared beside the library's separately selected game.

`LibraryScreen.closeActions()` now clears drawer selection/pressed states and
returns focus to the library before hiding the drawer. Shared frontend
navigation continues to own the game cursor. Focus is restored immediately,
so a setting that subsequently opens a dialog can take focus normally; the
animation completion does not steal focus back.

`LibraryMenuFixture` covers touch-open/Back, touch-open/scrim dismissal,
controller Menu/Back, touch-to-hat switching, exactly one selected visible
drawer row, no focused/selected/pressed/activated Menu button after closing,
no hidden drawer selection, and D-pad game selection afterward. It also
retains the full menu scrolling, held-hat and reopen-from-bottom checks.

The expanded fixture passed in both updated RGDS apps on October 2, 2026.
APK SHA-256: DOS `468884b91f80508999bb696007c9a907ba08f3c73f2163c53a6cbd364b0d4acd`;
PC98 `265a4779df038bfd7e9b12c7443e30c8313372e3063ef9fb06da57bd71dd5f4b`.
