# On-screen circle pads and profile defaults

Both hosts supply the active ControllerConfiguration layout to the shared on-screen controls. With Sticks enables left and right circle pads by default; Without Sticks keeps them off. Each profile has separate portrait and landscape layouts. Default stick positions sit below and slightly inward of the D-pad and face buttons. Existing non-stick visibility and customized positions are read from legacy saved layouts; untouched defaults adopt the new profile placement.

TouchStickView draws an opaque thumb inside an outlined circle. The thumb follows the owning pointer, clamps its center to the outer radius, clips at the rim, and recenters on release, cancellation, overlay hiding, rotation/profile changes or detachment. Each stick owns separate virtual input, permitting simultaneous movement. GamepadMapper applies the existing dead zone and bindings, proportional mouse movement, and the same left-stick-to-D-pad fallback as hardware controls. The four old right-stick direction buttons are replaced by one circle pad.

Touch-only controller setup starts without a selected or focused choice and with Continue disabled. The first D-pad Up/Down explicitly enters the action rows, including when Android is still in touch mode. Selection and navigation focus remain separate.

Validation: both Android builds and TouchStickFixture passed on RGDS, covering both sticks, diagonal travel and radius clamp, simultaneous input, release/cancel/hide, portrait/landscape positioning, profile switching, proportional mouse speed, left-stick fallback, initial setup focus, D-pad entry and selection. Rendered portrait/landscape layouts were visually inspected. Retroid was unavailable at its fixed endpoint and absent from mDNS.

The two-game EndSessionFixture also passed on RGDS: Commander Keen 4 to DOOM in DOS, and Rusty to Touhou 2 in PC-98. Overlays, settings return and native resumption remained working after teardown.
