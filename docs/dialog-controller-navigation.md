# Confirmation dialog controller navigation

Kairo #12, RGDS, October 2 2026.

The baseline Kairo98 Exit dialog opened with no focused control after entering
touch mode. Its local focus workaround only ran when the main window was
already outside touch mode. The shared adapter translated controller buttons
but otherwise delegated focus and directional movement to Android.

The shared adapter now owns button focus for plain confirmation dialogs.
Cancel is initially focused when present. Left/up and right/down move through
visible enabled buttons; confirm activates the focused button once, and Back
or controller B cancels. Hat input uses the same key path. Dialogs containing
a list or visible custom content continue using their existing navigation.

`ExitDialogFixture` opens each host's real private `confirmExit` action and
dispatches input through the resulting dialog decor/window, not the activity.
Run either host instrumentation with `-e exitDialog true`.

On RGDS, both Kairo98 and KairoDos passed:

- initial Cancel focus after `setInTouchMode(true)`;
- left/right keys and horizontal/vertical hat movement between buttons;
- controller A on Cancel keeps the activity open;
- controller B and Android Back cancel without finishing;
- controller A on Exit finishes the actual host activity.

The unmodified PC98 baseline failed at initial focus (`null`). Both updated
main APKs were installed with the existing app IDs/signatures and tested:

- DOS SHA-256: `18c30abf919914171ccdb6fe31ceaf8b5dbbf80a30c354cce13aaaf9040a830e`
- PC98 SHA-256: `d76647d04afa7e057bea7543fefa2d0f57abf4d646ed2679a10a9f6b4cce0b55`
