# Configuration changes during a game

Recreating a product's `MainActivity` stops its running machine, and a recreated
`SecondaryDisplayActivity` finds no pending session and closes, taking a swapped
game display with it. Attaching or detaching a hardware keyboard, toggling dark
mode, multi-window and foldable resizes, and display size, font, locale and
similar changes would otherwise recreate them.

Both product activities and the shared companion therefore declare every
configuration change Android lets an app handle:

```
mcc|mnc|locale|touchscreen|keyboard|keyboardHidden|navigation|screenLayout|fontScale|uiMode|orientation|density|screenSize|smallestScreenSize|layoutDirection|colorMode|fontWeightAdjustment|grammaticalGender
```

Neither product has configuration-specific resources: their themes are fixed
`Theme.Material.NoActionBar` styles with no night, locale or size variants. The
page stack, flyout, and an open tray are ordinary views in the activity, so they
survive a change handled in place. A
change handled in place reapplies the window's fullscreen policy and the guest
viewport. Physical keyboards need no reconfiguration; key events already reach
the input dispatcher, and `ControllerDeviceMonitor` releases a removed device's
held keys.

Views take their pixel and text sizes from density and font settings when they
are built. `ViewScale` records the settings a view tree was built with:

- A product activity rebuilds itself with `recreate()` when those settings
  change and no machine is running or starting. During a session it waits
  until the session ends or a pending launch fails.
- The companion rebuilds its content in place, so it and a swapped game
  display remain.

Android still recreates activities for changes an app cannot declare, such as
system theme overlays, and a killed process takes its machine with it.

## Device check

`ConfigurationChangeFixture` launched a game through the external launch
interface, checked that both activities declare the changes above, then toggled
dark mode and font scale twice each through the shell. After each change the same
game activity, session object and companion (when present) remained, and the
machine returned to running. The fixture was removed when the device tests moved to the UI tests ([ui-tests.md](ui-tests.md)).

Attaching and detaching a hardware keyboard needs a real or virtual input
device, so check it by hand during a game.
