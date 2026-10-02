# Shared directory picker

`DirectoryPicker` displays host-provided directory entries with opaque IDs. The
host supplies filesystem access and file selection behavior; the frontend owns
the dialog, directory history, worker, loading/error/empty states, retry,
controller navigation, and cancellation. Directory reads and sorting run on a
single worker. Generation checks discard results after navigation or dismissal.
Call `close()` when the owning session/activity ends.

Back and Up return to the parent directory. Back at the root and Cancel close
the picker. D-pad and confirm select directories/files through the shared dialog
controller handler. Android 13+ system Back uses the dialog's
`OnBackInvokedDispatcher`; key-event interception alone does not catch that path.
The optional shared Back action leaves existing dialogs' default cancellation
unchanged.

## RGDS verification, 2026-10-02

`DirectoryPickerFixture` ran in both KairoDos and Kairo98, using their actual
activity/display setup. It verified reads off the UI thread, failure/retry,
controller entry into a directory, system Back returning to its parent,
scrolling through 40 files to select the last off-screen entry, and cancellation
while a read was pending without a stale result reopening the dialog.

Both test runners accept `-e directoryPicker true`:

- DOS: `com.loxifi.kairodos.test/com.mrjackspade.kairodos.CatalogUpdateInstrumentation`
- PC98: `com.loxifi.kairo98.test/com.mrjackspade.kairo.frontend.LibraryUiInstrumentation`

This component is part of Kairo issue #25. The DOS executable selection/launch
integration is separate work; adding this shared UI does not complete that issue.
