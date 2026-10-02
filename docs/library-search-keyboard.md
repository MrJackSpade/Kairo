# Search keyboard dismissal

Kairo #10, tested on RGDS in both hosts, October 2 2026.

The search row provides a visible **View results** action. The Android IME's
Search/Done action, Enter, controller confirm/back and Android Back finish
text entry without clearing the query or opening a game. Focus moves to the
library container so the editor does not immediately reclaim it. Tapping the
search field reopens the Android IME. This does not use the guest keyboard.

Back handling and controller routing live in the shared frontend. While text
entry owns focus, cursor navigation belongs to Android rather than changing
the selected library game.

`LibraryKeyboardFixture` runs against each actual host activity with
`-e libraryKeyboard true`. On RGDS it taps the search field on its own display
to transfer Android window focus from the companion activity. It checks IME
visibility through window insets, the unchanged query, unfocused text field,
visible library, no game detail/menu activation, and no activity finish after:

- Android IME Search action;
- physical Back, controller B, and Enter;
- pre-IME Android Back;
- the visible View results action.

It also verifies reopening between cases and D-pad navigation after dismissal.
Both hosts passed. Initial fixture attempts requested only view focus while
the companion window held window focus; those failed to show the IME and
were corrected to use a real display-targeted tap.

## Search selection regression (Kairo #11)

The fixture additionally moves selection five entries down, taps the actual
Search icon, taps the search field, and checks the selected entry ID has not
changed. It verifies search focus and that no game detail opened. Controller A
is included alongside Enter and the IME Search action. Both hosts passed on
RGDS with the same installed application APKs below. No further production
change was required: #10 stopped frontend confirm handling from activating a
library game while search owned text focus.

Installed and tested APK SHA-256:

- DOS: `43621eacc844ed324b2e752c5ab89eb277b322bb86f7e1fd7d6cc92eca6fee7c`
- PC98: `60814c3bf4c19ab7cd7d3cc0a0f696a5896177fe60d7f8bd7c4e8bda5f6abc32`
