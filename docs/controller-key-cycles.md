# Shared key cycles

Kairo #16 adds D-pad Left/Right to the existing L1/R1 and L2/R2 cycle pairs.
`ControllerKeyCycles` supplies the accepted inputs, pair identity and direction
to the codec, editor and mapper. Host apps do not implement separate pairing.

- Left cycles backward; right cycles forward. First left selects the last key;
  first right selects the first key. Both directions wrap.
- Each pair and ordered sequence has its own cursor. Identical sequences in
  different pairs do not interfere. Different sequences in one pair are
  independent. A single mapped member also works.
- A held input advances once, until released. Replacing a profile releases held
  keys and resets cycle cursors. Other input owners still holding a key retain it.
- The editor's Save key cycle writes the selected pair together; other pairs are
  unchanged. Sequences accept 2–16 distinct valid guest keys, including letters.
- Other inputs do not gain cycle support. Cycles send keys; they cannot inspect
  game state, skip unavailable weapons, or follow keyboard weapon changes.

RGDS validation on 2026-10-02: `KeyCycleFixture` passed in both KairoDos and
Kairo98 using each host's actual guest key list. Checks cover nonnumeric A/B/C
sequences, all three pairs, direction/wrap, held input, lone mappings, mismatched
sequences, pair isolation, real Android hat events, profile replacement release,
codec round trip/rejection, and the actual editor's D-pad pair option and
controller-confirmed save. Tests use isolated in-memory mappings and do not
change saved user controls.

Run the apps' Android instrumentation runners with `-e keyCycle true`.
The DOS Doom profile is catalog data; the shared capability is installed before
publishing that profile. PC98's game defaults are unchanged.
