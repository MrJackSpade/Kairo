package com.mrjackspade.kairo.frontend

import android.app.AlertDialog
import android.content.Context

/** Product storage and catalog adapters for the common global/game touch editor. */
interface TouchSettingsStore<Game : Any> {
    fun global(): TouchInputSelection
    fun game(game: Game): TouchInputSelection
    fun secondaryTouchpad(game: Game): Boolean? = null
    fun saveGlobal(value: TouchInputSelection)
    fun saveGame(game: Game, value: TouchInputSelection, secondaryTouchpad: Boolean?)
    fun resetGame(game: Game)
}

/** Applies one mode ordering, hash gate, and Save/Reset flow to both emulators. */
class TouchSettingsCoordinator<Game : Any>(
    private val context: Context,
    private val store: TouchSettingsStore<Game>,
    private val contentId: (Game) -> String?,
    private val modes: List<InputModeDecider.Mode>,
    private val modeLabels: List<String>,
    private val globalTitle: String,
    private val gameTitle: (Game) -> String,
    private val directTouchExplanation: String,
    private val resetLabel: String = "Use catalog defaults",
    private val noHash: () -> Unit = {},
    private val changed: (Game?) -> Unit = {}
) {
    init { require(modes.size == modeLabels.size && modes.distinct().size == 3) }

    fun builder(game: Game? = null): AlertDialog.Builder? {
        if (game != null && contentId(game) == null) {
            noHash()
            return null
        }
        val value = if (game == null) store.global() else store.game(game)
        return TouchInputSettingsDialog.builder(context, TouchInputSettingsDialog.Options(
            title = if (game == null) globalTitle else gameTitle(game),
            modeLabels = modeLabels,
            modeIndex = modes.indexOf(value.mode).coerceAtLeast(0),
            directTouch = value.directTouch,
            secondaryTouchpad = game?.let(store::secondaryTouchpad),
            directTouchExplanation = directTouchExplanation,
            onSave = { index, direct, secondary ->
                val selected = TouchInputSelection(modes[index], direct)
                if (game == null) store.saveGlobal(selected)
                else store.saveGame(game, selected, secondary)
                changed(game)
            },
            onReset = game?.let { selected ->
                { store.resetGame(selected); changed(selected) }
            },
            resetLabel = resetLabel))
    }
}
