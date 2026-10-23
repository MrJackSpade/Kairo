package com.mrjackspade.kairo.frontend

import org.json.JSONObject

/**
 * One choice of the machine a game boots, such as its BASIC mode or CPU clock. A game's catalog
 * record or its own setting chooses it in `machine.<id>`; otherwise the all-games default, which
 * [MachineActivity] keeps in the preference [id], applies. [numeric] values are stored as numbers.
 *
 * Most options have a fixed list of choices. An option whose choices change, such as the firmware
 * files the player has imported, lists them when asked ([choicesOf]) and says which values it
 * keeps ([accepts]): a kept value whose choice has since gone stays the game's setting, and the
 * launch reports it. [actions] are rows below the choices, such as importing a new one; they get
 * the function that picks a value for whoever opened the choices (a game, or all games).
 */
class MachineOption(
    val id: String,
    val label: String,
    private val choicesOf: () -> List<ChoiceOption>,
    val default: String,
    val numeric: Boolean = false,
    private val accepts: ((String) -> Boolean)? = null,
    val actions: (select: (String) -> Unit) -> List<SettingsRow> = { emptyList() }
) {
    constructor(id: String, label: String, choices: List<ChoiceOption>, default: String, numeric: Boolean = false) :
        this(id, label, { choices }, default, numeric) {
        require(choices.any { it.id == default }) { "$id defaults to $default, which is not a choice" }
    }

    /** The choices as they are now. */
    val choices: List<ChoiceOption> get() = choicesOf()

    private fun accepted(value: String) = accepts?.invoke(value) ?: choices.any { it.id == value }

    /** The game's own choice, from its catalog layers, or null. */
    fun read(game: CatalogGame): String? =
        game.section(SECTION)?.opt(id)?.toString()?.takeIf(::accepted)

    fun valid(value: Any?): Boolean = (if (numeric) value is Int else value is String) && accepted(value.toString())

    fun stored(value: String): Any = if (numeric) value.toInt() else value

    companion object {
        const val SECTION = "machine"

        /** Whether [value] is a `machine` section holding only valid choices of [options]. */
        fun validSection(options: List<MachineOption>, value: Any): Boolean = value is JSONObject &&
            value.keys().asSequence().all { key -> options.firstOrNull { it.id == key }?.valid(value.opt(key)) == true }
    }
}
