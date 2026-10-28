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
 *
 * [ownFirmware] is the choice that starts on the player's own firmware, where the app also ships a
 * free one ([default]): once the player has imported that firmware it is the default instead
 * (MachineActivity.ownFirmwareInstalled), so firmware the player gave the app is what games use.
 */
class MachineOption(
    val id: String,
    val label: UiText,
    private val choicesOf: () -> List<ChoiceOption>,
    val default: String,
    val numeric: Boolean = false,
    private val accepts: ((String) -> Boolean)? = null,
    val actions: (select: (String) -> Unit) -> List<SettingsRow> = { emptyList() },
    val ownFirmware: String? = null
) {
    constructor(id: String, label: UiText, choices: List<ChoiceOption>, default: String, numeric: Boolean = false,
                ownFirmware: String? = null) :
        this(id, label, { choices }, default, numeric, ownFirmware = ownFirmware) {
        require(ownFirmware == null || choices.any { it.id == ownFirmware }) { "$id's own firmware $ownFirmware is not a choice" }
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
        const val SKIP_MEMORY_CHECK = "skipMemoryCheck"

        /** Skip memory check: the same option, on by default, in every app whose machine counts its memory at boot. */
        fun skipMemoryCheck() = MachineOption(SKIP_MEMORY_CHECK, UiText.res(R.string.kairo_option_skip_memory_check),
            listOf(ChoiceOption("on", UiText.res(R.string.kairo_on)), ChoiceOption("off", UiText.res(R.string.kairo_off))), "on")

        /** Whether [value] is a `machine` section holding only valid choices of [options]. */
        fun validSection(options: List<MachineOption>, value: Any): Boolean = value is JSONObject &&
            value.keys().asSequence().all { key -> options.firstOrNull { it.id == key }?.valid(value.opt(key)) == true }
    }
}
