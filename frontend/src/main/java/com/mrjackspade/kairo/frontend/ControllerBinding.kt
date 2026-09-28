package com.mrjackspade.kairo.frontend

/** Maps a physical or on-screen control to one guest input or app action. */
data class ControllerBinding(
    val input: String,
    val keys: List<Int> = emptyList(),
    val action: String? = null,
    val joystick: String? = null,
    val mouse: String? = null,
    /** Number hotkeys sent one at a time by a shoulder pair. */
    val cycleKeys: List<Int> = emptyList()
)
