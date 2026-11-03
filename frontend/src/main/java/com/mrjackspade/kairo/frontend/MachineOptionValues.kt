package com.mrjackspade.kairo.frontend

/**
 * The values a game's machine options take (MachineActivity.optionValues), apart from where the
 * choices are stored, so unit tests reach them (Kairo#112): a game's own choice, else the player's
 * all-games choice, else the player's own firmware where they imported it, else the option's default.
 */
object MachineOptionValues {
    /**
     * The all-games default of [option]: [stored], the player's all-games choice, when it is one of
     * the option's; else its own firmware choice where [ownFirmwareInstalled]; else its default.
     */
    fun default(option: MachineOption, stored: String?, ownFirmwareInstalled: () -> Boolean): String =
        stored?.takeIf { value -> option.choices.any { it.id == value } }
            ?: option.ownFirmware?.takeIf { ownFirmwareInstalled() }
            ?: option.default

    /**
     * Every option's value, by option ID: [own] (the game's own choice), else its all-games default.
     * The options without firmware of their own come first, as whether the player's firmware is
     * imported can depend on them (the TOS for the game's model): [ownFirmwareInstalled] is given
     * the values found so far.
     */
    fun values(options: List<MachineOption>, own: (MachineOption) -> String?, stored: (MachineOption) -> String?,
               ownFirmwareInstalled: (MachineOption, Map<String, String>) -> Boolean): Map<String, String> {
        val values = LinkedHashMap<String, String>()
        for (option in options.sortedBy { it.ownFirmware != null })
            values[option.id] = own(option) ?: default(option, stored(option)) { ownFirmwareInstalled(option, values) }
        return options.associate { it.id to values.getValue(it.id) }
    }
}
