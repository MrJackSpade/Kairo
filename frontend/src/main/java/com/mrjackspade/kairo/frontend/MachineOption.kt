package com.mrjackspade.kairo.frontend

import org.json.JSONObject

/**
 * One choice of the machine a game boots, such as its BASIC mode or CPU clock. A game's catalog
 * record or its own setting chooses it in `machine.<id>`; otherwise the all-games default, which
 * [MachineActivity] keeps in the preference [id], applies. [numeric] values are stored as numbers.
 */
class MachineOption(
    val id: String,
    val label: String,
    val choices: List<ChoiceOption>,
    val default: String,
    val numeric: Boolean = false
) {
    init { require(choices.any { it.id == default }) { "$id defaults to $default, which is not a choice" } }

    /** The game's own choice, from its catalog layers, or null. */
    fun read(game: CatalogGame): String? =
        game.section(SECTION)?.opt(id)?.toString()?.takeIf { value -> choices.any { it.id == value } }

    fun valid(value: Any?): Boolean = (if (numeric) value is Int else value is String) &&
        choices.any { it.id == value.toString() }

    fun stored(value: String): Any = if (numeric) value.toInt() else value

    companion object {
        const val SECTION = "machine"

        /** Whether [value] is a `machine` section holding only valid choices of [options]. */
        fun validSection(options: List<MachineOption>, value: Any): Boolean = value is JSONObject &&
            value.keys().asSequence().all { key -> options.firstOrNull { it.id == key }?.valid(value.opt(key)) == true }
    }
}
