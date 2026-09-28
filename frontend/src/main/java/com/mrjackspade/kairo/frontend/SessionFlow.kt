package com.mrjackspade.kairo.frontend

/** Owns the open/close lifecycle of the same in-game session menu in every app. */
class SessionFlow(
    val drawer: SessionDrawer,
    private val title: () -> String,
    private val status: () -> String,
    private val prepareOpen: () -> Unit,
    private val visibilityChanged: (Boolean) -> Unit,
    private val focusGame: () -> Unit,
    private val afterOpen: () -> Unit = {}
) {
    var isOpen = false
        private set

    fun open() {
        if (isOpen) return
        prepareOpen()
        isOpen = true
        visibilityChanged(true)
        drawer.open(title(), status())
        afterOpen()
    }

    fun close() {
        if (!isOpen) return
        isOpen = false
        drawer.close()
        visibilityChanged(false)
        focusGame()
    }

    fun reset() {
        isOpen = false
        drawer.close()
    }
}
