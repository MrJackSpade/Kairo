# Shared directory picker

`DirectoryPicker` is a page on the shared stack that displays host-provided
directory entries with opaque IDs. The scope chip shows the path. The host supplies
filesystem access and file selection behavior; the frontend owns the page, directory
history, worker, loading/error/empty states, retry, controller navigation, and
cancellation. Folders are rows with a chevron; files are action rows. Directory reads and sorting run on a
single worker. Generation checks discard results after navigation or dismissal.
Call `close()` when the owning session/activity ends. Hosts may supply `rootEntries` for actions shown before the sorted directory listing at the root only; selection uses the same callback as files.

Back returns to the parent directory. Back at the root closes the picker and
reports cancellation. D-pad and confirm select directories and files through the
page stack, which receives the activity's key and hat dispatch, Android 13+ system
Back through the host's `FrontendBackCoordinator`, and companion-screen keys, hats,
and Back through `SecondaryDisplayCoordinator`.

## RGDS verification, 2026-10-02

`DirectoryPickerFixture` ran in both KairoDos and Kairo98, using their actual
activity/display setup, against the earlier dialog version. It verified reads off the UI thread, failure/retry,
controller entry into a directory, system Back returning to its parent,
scrolling through 40 files to select the last off-screen entry, and cancellation
while a read was pending without a stale result reopening the picker. The fixture
now drives the page through the host's page stack. The fixture
also exercises companion-screen confirm/Back/scroll after entering touch mode;
Exit confirmations are checked through companion key and hat input in both hosts
by `ExitDialogFixture`, now against the game flyout's inline Exit confirm.

The fixture was removed when the device tests moved to the UI tests ([ui-tests.md](ui-tests.md)).

This component is part of Kairo issue #25. Executable selection and launch remain
DOS-host responsibilities; see KairoDos `docs/dos-program-picker.md`.
