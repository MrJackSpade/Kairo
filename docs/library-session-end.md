# Returning from a game to Library

Kairo #13, tested in both applications on RGDS, October 2 2026.

The user-facing Library action calls shared `SessionNavigationCoordinator.requestLibrary`.
With an active game it asks **End game session?**, explains that unsaved
progress will be lost, and offers Cancel / End session. Cancel closes the
session menu and preserves the existing game. Confirmation waits for the
host's teardown callback before showing Library. No running game means no
prompt. Internal startup/teardown paths still use `showLibrary` directly.

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
existing external launch interface and the actual session menu's Library
button. It does not edit game configuration. RGDS checks passed with Doom in
DOS and Brandish 2 Renewal in PC98:

- Cancel resumes the same session object, leaves Library hidden, and closes
  the menu.
- Confirm reaches Library without finishing the app; native status is stopped
  and input holds are empty.
- DOS game/audio thread references, AudioTrack, game root and session flow
  are cleared. PC98 current media/game are cleared and native audio is off.
- Library while idle does not prompt.
- The same game launches again and a second complete teardown succeeds.
- DOS also passes an early return with native status `1` (loading), before
  waiting for the machine to be running.

Installed/tested APK SHA-256:

- DOS: `69814657b3072fbd6a4cccf8fe9464555b0cda0d582621fd0f7dc0a2212ef820`
- PC98: `656851946d09173e0b6d9dc1c26b7120c0a07bb5410093b180dd52b71a5477c7`
