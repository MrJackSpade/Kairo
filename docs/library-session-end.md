# Returning from a game to Library

Kairo #13, tested in both applications on RGDS, October 2 2026.

The game flyout's **Return to library** row comes from shared
`SessionNavigationCoordinator.returnRow`. With an active game it asks first in a
confirmation modal titled "Return to library?": "This game stops. Progress since your
last save is lost." with Cancel and End game, focused on Cancel. Cancel closes the modal
and leaves the flyout open; Menu then returns to the same game. No running game
means no confirmation. Internal startup and teardown paths still use `showLibrary`
directly.

For a game started from the app's own library, End game waits for the host's teardown
callback before showing Library. A game launched by an external frontend closes the
app and returns to the frontend instead: the row reads **Return to frontend** and its
confirmation, titled "Return to frontend?", says "This game stops and Kairo98 closes"
(or KairoDos). The coordinator
reads the host's frontend flag when it builds the row, because each host clears that
flag during teardown. Confirming then calls the host's existing exit path, which stops
the machine itself.

Native teardown remains in each emulator adapter:

- DOS invalidates launch/audio work, releases input, stops AudioTrack, requests
  native shutdown, and joins the game and audio threads off the UI thread.
  Native stop is asynchronous, so merely calling it is insufficient. During
  startup, native initialization can reset its stop flag; cancellation keeps
  requesting stop until the launch thread has returned. Existing session
  cleanup then runs and releases session views/references before Library.
- PC98 invalidates startup/disk-swap work, releases input, and joins native
  shutdown off the UI thread. That native path stops/closes AAudio, joins the
  presenter, and stops/flushes the machine. The adapter clears current media,
  title, game, controller session, menu and keyboard state before Library.

`EndSessionFixture`, run with `-e endSessionUri '<stored game URI>'`, uses the
existing external launch interface and the actual game flyout's Return row. It does not
edit game configuration. To exercise the library return, it clears the host's frontend
flag on the running session before confirming. It checks:

- The confirmation modal opens on Cancel with Cancel and End game, and its title and
  message say whether it returns to the frontend.
- Cancel keeps the same session object and the flyout open with Library hidden; closing
  the menu resumes the game.
- End game reaches Library without finishing the app; native status is stopped and
  input holds are empty.
- DOS game/audio thread references, AudioTrack, and game root are cleared, and no page
  stays open. PC98 current media/game are cleared and native audio is off.
- Returning while idle does not ask first.
- The same game launches again and a second complete teardown succeeds.
- DOS also passes an early return with native status `1` (loading), before
  waiting for the machine to be running.
- A frontend-launched game finishes the app on confirmation without showing
  Library, and its machine stops.
- Controls › Arrange layout opens over the running game, hides the in-game on-screen
  controls, returns to Controls on Back, and the controls come back when the menu closes.

The October 2 RGDS run used the earlier End game session dialog, with Doom in DOS and
Brandish 2 Renewal in PC98. It passed every check of that version except the frontend
exit, which it predates. Installed/tested APK SHA-256:

- DOS: `69814657b3072fbd6a4cccf8fe9464555b0cda0d582621fd0f7dc0a2212ef820`
- PC98: `656851946d09173e0b6d9dc1c26b7120c0a07bb5410093b180dd52b71a5477c7`
