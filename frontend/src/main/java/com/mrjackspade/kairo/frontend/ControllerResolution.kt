package com.mrjackspade.kairo.frontend

/**
 * What a press does, from the physical inputs' controls (PhysicalControllerBindings) and the
 * mapping's bindings (ControllerDefaults.cascade).
 *
 * A physical input bound directly comes before the control it is mapped to, and a control does what
 * a physical input mapped to it is bound to directly, so an on-screen button does what its physical
 * button does. Direct bindings are a game's stick press in a catalog that predates the L3 and R3
 * controls (KairoDos), which released builds read and could not parse as virtual:l3 (Kairo#141), and
 * old saved profiles.
 */
object ControllerResolution {
    /** What the physical [input] (button:, axis: or hat:) does. */
    fun forInput(input: String, physical: List<PhysicalControllerBinding>,
                 bindings: List<ControllerBinding>): ControllerBinding? {
        bindings.firstOrNull { it.input == input }?.let { return it }
        val control = physical.firstOrNull { it.input == input }?.control ?: return null
        return bindings.firstOrNull { it.input == "virtual:$control" }
    }

    /** What [control] (PhysicalControllerBindings.controls) does, as an on-screen button presses it. */
    fun forControl(control: String, physical: List<PhysicalControllerBinding>,
                   bindings: List<ControllerBinding>): ControllerBinding? =
        physical.asSequence().filter { it.control == control }
            .firstNotNullOfOrNull { mapped -> bindings.firstOrNull { it.input == mapped.input } }
            ?: bindings.firstOrNull { it.input == "virtual:$control" }
}
