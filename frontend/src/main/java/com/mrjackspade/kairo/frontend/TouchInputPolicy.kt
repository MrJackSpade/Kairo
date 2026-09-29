package com.mrjackspade.kairo.frontend

/** Effective touch behavior and the source of each independently overridden choice. */
data class TouchInputSelection(
    val mode: InputModeDecider.Mode,
    val directTouch: Boolean
)

data class ScopedTouchInput(
    val selection: TouchInputSelection,
    val gameMode: Boolean,
    val gameTouch: Boolean
) {
    fun label(): String {
        val mode = when (selection.mode) {
            InputModeDecider.Mode.AUTO -> "Auto"
            InputModeDecider.Mode.KEYBOARD -> "Keyboard"
            InputModeDecider.Mode.MOUSE -> "Mouse"
        }
        val touch = if (selection.directTouch) "direct tap" else "touchpad"
        return "$mode (${if (gameMode) "Game" else "Global"}) · " +
            "$touch (${if (gameTouch) "Game" else "Global"})"
    }
}

object TouchInputPolicy {
    fun resolve(global: TouchInputSelection,
                gameMode: InputModeDecider.Mode? = null,
                gameDirectTouch: Boolean? = null): ScopedTouchInput = ScopedTouchInput(
        TouchInputSelection(gameMode ?: global.mode, gameDirectTouch ?: global.directTouch),
        gameMode != null, gameDirectTouch != null)
}
