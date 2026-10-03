# Shared directory picker

`DirectoryPicker` displays host-provided directory entries with opaque IDs. The
host supplies filesystem access and file selection behavior; the frontend owns
the dialog, directory history, worker, loading/error/empty states, retry,
controller navigation, and cancellation. Directory reads and sorting run on a
single worker. Generation checks discard results after navigation or dismissal.
Call `close()` when the owning session/activity ends. Hosts may supply `rootEntries` for actions shown before the sorted directory listing at the root only; selection uses the same callback as files.

Back and Up return to the parent directory. Back at the root and Cancel close
the picker. D-pad and confirm select directories/files through the shared dialog
controller handler. Android 13+ system Back uses the dialog's
`OnBackInvokedDispatcher`; key-event interception alone does not catch that path.
The optional shared Back action leaves existing dialogs' default cancellation
unchanged. Companion-screen keys, hats, and system Back route to the frontmost
styled dialog before the host activity. Dialogs are tracked weakly and removed
on detach. Forwarded controller input leaves touch mode, just like primary-window
input, so touching the companion keyboard cannot strand list selection.

## RGDS verification, 2026-10-02

`DirectoryPickerFixture` ran in both KairoDos and Kairo98, using their actual
activity/display setup. It verified reads off the UI thread, failure/retry,
controller entry into a directory, system Back returning to its parent,
scrolling through 40 files to select the last off-screen entry, and cancellation
while a read was pending without a stale result reopening the dialog. The fixture
also exercises companion-screen confirm/Back/scroll after entering touch mode;
it waits for the touch-mode transition before forwarding keys. Existing exit
confirmations are checked through companion key and hat input in both hosts.

Both test runners accept `-e directoryPicker true`:

- DOS: `com.loxifi.kairodos.test/com.mrjackspade.kairodos.CatalogUpdateInstrumentation`
- PC98: `com.loxifi.kairo98.test/com.mrjackspade.kairo.frontend.LibraryUiInstrumentation`

This component is part of Kairo issue #25. Executable selection and launch remain
DOS-host responsibilities; see KairoDos `docs/dos-program-picker.md`.
