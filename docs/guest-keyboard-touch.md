# Guest keyboard touch coverage

Both hosts must send video touches and otherwise-unhandled touches on the full
video background through `TouchUiCoordinator.handleKeyboardTouch`. Binding only
the scaled SurfaceView leaves letterbox and portrait-screen space inert.
Child on-screen controller buttons keep their own touch targets. Auto uses the
existing InputModeDecider; it does not force keyboard mode on mouse-driven games.

## Device regression (October 2, 2026)

The shared `TouchKeyboardFixture` runs through the actual host activity's touch
dispatch against a directly launched installed game. It stops the companion
screen for a single-screen test and narrows the app root to 320 pixels on the
RGDS's 640x480 display, reproducing portrait-shaped letterboxing without changing
system display settings. Temporary input settings and control enablement are
restored; no game files or mappings are edited.

Before the fix, both KairoDos (Doom) and Kairo98 (Steam Heart's) opened the keyboard
from the video, but failed on the surrounding game background in Keyboard mode.
After the fix both apps passed all ten checks each:

- Keyboard and Auto (reset to its keyboard fallback) on video and surrounding space.
- Each combination with on-screen controls shown and hidden.
- Actual on-screen A button presses do not open the keyboard in either mode.

This verifies routing and visibility, not every game's native Auto-detection
heuristic. The user's phone was not accessed.

Invoke with `am instrument -w -e touchKeyboardUri '<granted-game-uri>'` and the
app's existing test runner. Use the actual library URI; do not search the UI.
