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
 *
 * An option with a [question] is one a game must have answered: a game no catalog knows and the
 * player has not set it for is asked it at launch (MachineActivity.launch), and the answer is kept
 * as the game's own setting, which stays changeable in its settings. A catalogued game without a
 * value takes [default]: the catalog names the games that differ (the FM Towns Marty's).
 *
 * An option can depend on others (Kairo#143), such as the processors a chosen PC takes: its choices
 * and its default come from the values of the options it [dependsOn] ([choicesWith], [defaultWith]),
 * by option ID. A game's value that the others no longer allow is not replaced: the player is asked
 * for a new one (MachineActivity).
 */
class MachineOption(
    val id: String,
    val label: UiText,
    private val choicesOf: () -> List<ChoiceOption>,
    val default: String,
    val numeric: Boolean = false,
    private val accepts: ((String) -> Boolean)? = null,
    val actions: (select: (String) -> Unit) -> List<SettingsRow> = { emptyList() },
    val ownFirmware: String? = null,
    val question: UiText? = null,
    val dependsOn: List<String> = emptyList(),
    private val choicesWith: ((Map<String, String>) -> List<ChoiceOption>)? = null,
    private val defaultWith: ((Map<String, String>) -> String)? = null,
    /** How a value reads where no choice names it, such as a default the machine's choices do not include. */
    val labelOf: ((String) -> UiText)? = null,
    /**
     * A value the player can type beside the choices (Custom…, Kairo#183), for a game whose options
     * have the values given, for a setting that could be any number in a range; null for none.
     */
    private val customWith: ((Map<String, String>) -> CustomValue)? = null
) {
    constructor(id: String, label: UiText, choices: List<ChoiceOption>, default: String, numeric: Boolean = false,
                ownFirmware: String? = null, question: UiText? = null) :
        this(id, label, { choices }, default, numeric, ownFirmware = ownFirmware, question = question) {
        require(ownFirmware == null || choices.any { it.id == ownFirmware }) { "$id's own firmware $ownFirmware is not a choice" }
        require(choices.any { it.id == default }) { "$id defaults to $default, which is not a choice" }
    }

    /** The choices as they are now. */
    val choices: List<ChoiceOption> get() = choicesOf()

    /** The choices of a game whose options have [values] (by option ID): the ones its [dependsOn] allow. */
    fun choices(values: Map<String, String>): List<ChoiceOption> = choicesWith?.invoke(values) ?: choices

    /** The default of a game whose options have [values]. */
    fun default(values: Map<String, String>): String = defaultWith?.invoke(values) ?: default

    /** The typed value a game whose options have [values] can take, or null. */
    fun custom(values: Map<String, String>): CustomValue? = customWith?.invoke(values)

    /** Whether a game whose options have [values] can keep [value]: one of its choices, or a typed value that fits. */
    fun fits(value: String, values: Map<String, String>): Boolean =
        choices(values).any { it.id == value } || custom(values)?.fits(value) == true

    // A typed value is a whole number; whether it fits the game's other options is [fits]'s to say.
    private fun accepted(value: String) = accepts?.invoke(value) ?: (choices.any { it.id == value } ||
        customWith != null && value.toIntOrNull() != null)

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

        const val HARDWARE_ACCELERATION = "hardwareAcceleration"

        /**
         * Hardware acceleration (KairoX86#282): on, the emulated graphics hardware's work that has a GPU
         * version runs on the device's GPU; off, all of it runs in software, the reference the GPU version
         * matches within the graphics batch's margin (KairoX86#283). Read at the machine's start.
         */
        fun hardwareAcceleration(default: String) = MachineOption(HARDWARE_ACCELERATION,
            UiText.res(R.string.kairo_option_hardware_acceleration),
            listOf(ChoiceOption("on", UiText.res(R.string.kairo_on)), ChoiceOption("off", UiText.res(R.string.kairo_off))), default)

        /** Whether [value] is a `machine` section holding only valid choices of [options]. */
        fun validSection(options: List<MachineOption>, value: Any): Boolean = value is JSONObject &&
            value.keys().asSequence().all { key -> options.firstOrNull { it.id == key }?.valid(value.opt(key)) == true }
    }
}
