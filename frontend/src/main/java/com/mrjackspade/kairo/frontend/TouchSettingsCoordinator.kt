package com.mrjackspade.kairo.frontend

/** Product storage and catalog adapters for the common global and per-game touch rows. */
interface TouchSettingsStore<Game : Any> {
    fun global(): TouchInputSelection
    fun saveGlobal(value: TouchInputSelection)
    fun mode(game: Game): GameValue<InputModeDecider.Mode>
    fun directTouch(game: Game): GameValue<Boolean>
    /** Null when the app has no per-game second-screen choice. */
    fun secondaryTouchpad(game: Game): GameValue<Boolean>? = null
}

/** One mode ordering and the same rows for all games and for one game, in both emulators. */
class TouchSettingsCoordinator<Game : Any>(
    private val store: TouchSettingsStore<Game>,
    /** Auto, Keyboard, and Mouse, each with an optional description. */
    private val modeOptions: List<Pair<InputModeDecider.Mode, ChoiceOption>>,
    private val directTouchExplanation: String,
    private val changed: (Game?) -> Unit = {}
) {
    init { require(modeOptions.map { it.first }.distinct().size == 3) }

    private val modes = modeOptions.map { it.second }
    private val touches = listOf(ChoiceOption("touchpad", "Touchpad", "Drag to move, tap to click"),
        ChoiceOption("direct", "Direct tap", "Click where you touch"))
    private fun modeId(mode: InputModeDecider.Mode) = modeOptions.first { it.first == mode }.second.id
    private fun mode(id: String) = modeOptions.first { it.second.id == id }.first
    private fun touchId(direct: Boolean) = if (direct) "direct" else "touchpad"

    /** Controls › TOUCH SCREEN, for all games. */
    fun globalGroup() = RowGroup("TOUCH SCREEN", listOf(
        SettingsRow.choice("tap", "Screen tap", ChoiceSpec("Screen tap", { GameSource.ALL_GAMES },
            { listOf(ChoiceGroup(null, modes)) }, { modeId(store.global().mode) }, select = { id ->
            store.saveGlobal(store.global().copy(mode = mode(id)))
            changed(null)
        })),
        SettingsRow.choice("mouse-touch", "Mouse touch", ChoiceSpec("Mouse touch", { GameSource.ALL_GAMES },
            { listOf(ChoiceGroup(null, touches)) }, { touchId(store.global().directTouch) }, select = { id ->
            store.saveGlobal(store.global().copy(directTouch = id == "direct"))
            changed(null)
        }))), directTouchExplanation)

    /** Game settings › CONTROLS rows for one game. */
    fun gameRows(game: Game, scope: () -> String, enabled: () -> Boolean): List<SettingsRow> {
        fun <V> mapped(value: GameValue<V>, id: (V) -> String, back: (String) -> V) = GameValue(
            { value.custom()?.let(id) }, { id(value.fallback()) }, value.source,
            { picked -> value.set(picked?.let(back)); changed(game) })
        val reason = { GameSettingsRows.NO_HASH }
        return listOfNotNull(
            GameSettingsRows.choice("game-tap", "Screen tap", scope, modes,
                mapped(store.mode(game), ::modeId, ::mode), enabled, reason),
            GameSettingsRows.choice("game-mouse-touch", "Mouse touch", scope, touches,
                mapped(store.directTouch(game), ::touchId) { it == "direct" }, enabled, reason),
            store.secondaryTouchpad(game)?.let { secondary ->
                GameSettingsRows.choice("game-second-screen", "Second screen starts with", scope,
                    listOf(ChoiceOption("keyboard", "Keyboard"), ChoiceOption("touchpad", "Mouse touchpad")),
                    mapped(secondary, { if (it) "touchpad" else "keyboard" }) { it == "touchpad" },
                    enabled, reason)
            })
    }
}
