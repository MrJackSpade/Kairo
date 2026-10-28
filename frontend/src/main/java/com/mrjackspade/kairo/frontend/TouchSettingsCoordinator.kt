package com.mrjackspade.kairo.frontend

import android.content.Context
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
    private val context: Context,
    private val store: TouchSettingsStore<Game>,
    /** Auto, Keyboard, and Mouse, each with an optional description. */
    private val modeOptions: List<Pair<InputModeDecider.Mode, ChoiceOption>>,
    private val directTouchExplanation: UiText,
    private val changed: (Game?) -> Unit = {},
    /** False when the guest mouse only moves relatively: Mouse touch is then the touchpad, with no rows. */
    private val directTouchAvailable: Boolean = true
) {
    init { require(modeOptions.map { it.first }.distinct().size == 3) }

    private val modes = modeOptions.map { it.second }
    private val touches = listOf(
        ChoiceOption("touchpad", UiText.res(R.string.kairo_touch_touchpad), UiText.res(R.string.kairo_touch_touchpad_help)),
        ChoiceOption("direct", UiText.res(R.string.kairo_touch_direct), UiText.res(R.string.kairo_touch_direct_help)))
    private fun str(id: Int): String = context.getString(id)
    private fun modeId(mode: InputModeDecider.Mode) = modeOptions.first { it.first == mode }.second.id
    private fun mode(id: String) = modeOptions.first { it.second.id == id }.first
    private fun touchId(direct: Boolean) = if (direct) "direct" else "touchpad"

    /** Controls › TOUCH SCREEN, for all games. */
    fun globalGroup() = RowGroup(str(R.string.kairo_touch_group), listOfNotNull(
        SettingsRow.choice("tap", str(R.string.kairo_touch_screen_tap), ChoiceSpec(str(R.string.kairo_touch_screen_tap), { GameSource.ALL_GAMES },
            { listOf(ChoiceGroup(null, modes)) }, { modeId(store.global().mode) }, select = { id ->
            store.saveGlobal(store.global().copy(mode = mode(id)))
            changed(null)
        })),
        if (!directTouchAvailable) null else
        SettingsRow.choice("mouse-touch", str(R.string.kairo_touch_mouse_touch), ChoiceSpec(str(R.string.kairo_touch_mouse_touch), { GameSource.ALL_GAMES },
            { listOf(ChoiceGroup(null, touches)) }, { touchId(store.global().directTouch) }, select = { id ->
            store.saveGlobal(store.global().copy(directTouch = id == "direct"))
            changed(null)
        }))), if (directTouchAvailable) directTouchExplanation.resolve(context) else null)

    /** Game settings › CONTROLS rows for one game. */
    fun gameRows(game: Game, scope: () -> String, enabled: () -> Boolean): List<SettingsRow> {
        fun <V> mapped(value: GameValue<V>, id: (V) -> String, back: (String) -> V) = GameValue(
            { value.custom()?.let(id) }, { id(value.fallback()) }, value.source,
            { picked -> value.set(picked?.let(back)); changed(game) })
        val reason = { GameSettingsRows.noHash(context) }
        return listOfNotNull(
            GameSettingsRows.choice(context, "game-tap", str(R.string.kairo_touch_screen_tap), scope, modes,
                mapped(store.mode(game), ::modeId, ::mode), enabled, reason),
            if (!directTouchAvailable) null else
            GameSettingsRows.choice(context, "game-mouse-touch", str(R.string.kairo_touch_mouse_touch), scope, touches,
                mapped(store.directTouch(game), ::touchId) { it == "direct" }, enabled, reason),
            store.secondaryTouchpad(game)?.let { secondary ->
                GameSettingsRows.choice(context, "game-second-screen", str(R.string.kairo_touch_second_screen), scope,
                    listOf(ChoiceOption("keyboard", UiText.res(R.string.kairo_keyboard)),
                        ChoiceOption("touchpad", UiText.res(R.string.kairo_touch_mouse_touchpad))),
                    mapped(secondary, { if (it) "touchpad" else "keyboard" }) { it == "touchpad" },
                    enabled, reason)
            })
    }
}
