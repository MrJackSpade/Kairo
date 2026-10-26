package com.mrjackspade.kairo.frontend

/** Maps a physical or on-screen control to one guest input or app action. */
data class ControllerBinding(
    val input: String,
    val keys: List<Int> = emptyList(),
    val action: String? = null,
    /** A joystick input, sent alone or together with [keys]. */
    val joystick: String? = null,
    val mouse: String? = null,
    /** Guest keys sent one at a time by a shoulder or D-pad left/right pair. */
    val cycleKeys: List<Int> = emptyList(),
    /** Relative mouse movement multiplier; touch/pointer input is unaffected. */
    val mouseSpeed: Float = 1f
) {
    /**
     * Sends nothing: an explicit null. In a player's saved layer it removes the input the layers
     * below bind (ControllerDefaults.cascade); nothing we ship holds one.
     */
    val unbound: Boolean get() = keys.isEmpty() && cycleKeys.isEmpty() && action == null && joystick == null && mouse == null
}
