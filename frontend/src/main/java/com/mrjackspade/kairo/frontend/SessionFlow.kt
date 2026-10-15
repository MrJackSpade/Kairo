package com.mrjackspade.kairo.frontend

/**
 * Owns the open and close lifecycle of the game flyout in every app. While a game runs, any
 * open page counts as the menu: the game pauses, and closing the last page resumes it.
 */
class SessionFlow(
    val pages: PageStack,
    private val model: () -> FlyoutModel,
    private val inGame: () -> Boolean,
    private val prepareOpen: () -> Unit,
    private val visibilityChanged: (Boolean) -> Unit,
    private val focusGame: () -> Unit,
    private val afterOpen: () -> Unit = {}
) {
    private var gameMenu = false

    init {
        pages.onActiveChanged { active ->
            if (active && inGame()) {
                gameMenu = true
                prepareOpen()
                visibilityChanged(true)
                afterOpen()
            } else if (!active && gameMenu) {
                gameMenu = false
                visibilityChanged(false)
                focusGame()
            }
        }
    }

    val isOpen: Boolean get() = gameMenu && pages.isActive

    /** [start] focuses a row; [confirm] also opens its confirmation. Only a game has this menu. */
    fun open(start: String? = null, confirm: Boolean = false) {
        if (pages.isActive || !inGame()) return
        pages.push(FlyoutPage(model(), start, confirm))
    }

    /**
     * The controller's menu, pause, restart and exit actions. Returns false for an action the
     * app handles itself, such as fast forward.
     */
    fun controllerAction(action: String, state: SessionNavigationState, applyPauseState: () -> Unit): Boolean {
        when (action) {
            "menu" -> if (isOpen) close() else open()
            "pause" -> {
                state.userPaused = !state.userPaused
                applyPauseState()
                Ui.message(pages.activity, if (state.userPaused) "Paused" else "Resumed")
            }
            "restart" -> if (!isOpen) open("f-restart", confirm = true)
            "exit" -> if (!isOpen) open("f-exit", confirm = true)
            else -> return false
        }
        return true
    }

    fun close() = pages.closeAll()

    /** A session ending or starting clears every page without the closing motion mattering. */
    fun reset() = pages.closeAll()
}
