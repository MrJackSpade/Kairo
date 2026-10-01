# Controller configuration

Both products use the shared `ControllerConfiguration` selector and `ControllerProfileStore`.
On first launch (including upgrades), a detected controller offers Auto, With Sticks, and
Without Sticks. Auto starts selected and focused. Without a detected controller there is
no Auto option, and Continue stays disabled until a configuration is selected. Back exits
required setup. The global Controller menu offers the same selector later.

Auto requires centered X/Y axes and either centered Z/RZ or RX/RY axes on the same input
device. Hat axes and triggers do not count. Zero or one stick selects Without Sticks.
Built-in devices take precedence. When firmware reports the handheld controller as
external, its descriptor is remembered so adding another external pad does not replace it.
Automatic capability changes are deferred during a game session; explicit menu changes
apply after releasing held inputs. Each product stores its own selection.

Catalog defaults use `defaults: {"withoutSticks": [...], "withSticks": [...]}` inside a
controller record/preset. Without Sticks is required; With Sticks is optional. Missing
With Sticks falls back to Without Sticks. An explicitly empty array disables bindings.
Legacy catalog `bindings` migrate to Without Sticks without changing their assignments.
Keep legacy `bindings` alongside generated defaults for older catalog clients.

Saved global overrides are copied once into both configuration-specific preference keys.
The migration retains the legacy key, never replaces existing new keys, and does not
rewrite physical mappings or per-game overrides. Resetting a global mapping affects only
the selected configuration. Explicit per-game overrides continue to take precedence.

Touchscreen controls, their defaults, and saved arrangements are independent of this
selection. They continue to send the existing virtual controls through the chosen game
mapping. This feature does not change touchscreen controls or the arrangement editor.

Run `:frontend:testDebugUnitTest` to verify capability detection, device precedence,
session deferral, missing versus empty variants, and migration/reset behavior without a
device. Both applications must pin the same shared commit.
