package com.mrjackspade.kairo.frontend

/** Maps a physical or on-screen control to one guest input or app action. */
data class ControllerBinding(
    val input: String,
    val keys: List<Int> = emptyList(),
    val action: String? = null,
    val joystick: String? = null,
    val mouse: String? = null,
    /** Guest keys sent one at a time by a shoulder or D-pad left/right pair. */
    val cycleKeys: List<Int> = emptyList(),
    /** Relative mouse movement multiplier; touch/pointer input is unaffected. */
    val mouseSpeed: Float = 1f
)
