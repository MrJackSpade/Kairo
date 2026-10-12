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

    /** [start] focuses a row; [confirm] also opens its confirmation. */
    fun open(start: String? = null, confirm: Boolean = false) {
        if (pages.isActive) return
        pages.push(FlyoutPage(model(), start, confirm))
    }

    fun close() = pages.closeAll()

    /** A session ending or starting clears every page without the closing motion mattering. */
    fun reset() = pages.closeAll()
}
