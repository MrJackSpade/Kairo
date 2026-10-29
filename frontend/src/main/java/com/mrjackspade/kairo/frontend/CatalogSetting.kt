package com.mrjackspade.kairo.frontend

import org.json.JSONArray
import org.json.JSONObject

/**
 * A setting a catalog record can give a game, named by where it sits in the record. A product's
 * catalog schema lists its settings ([LayeredGameCatalog.Schema.settings]) and the game settings
 * page shows a row for each one, so whatever the catalog decides for a game the player can change
 * for that game: a game the catalog got wrong, or the player's own copy of it.
 */
sealed class CatalogSetting(val field: String, val subfield: String,
                             /** Whether the setting matters to a game of this content ID; its row shows only then. */
                             val appliesTo: (String) -> Boolean = { true }) {
    /** Where the setting sits in a record, such as machine.basic or media.floppyB. */
    val path: String = if (subfield.isEmpty()) field else "$field.$subfield"

    /** Every place in a record its row changes. */
    open val paths: Set<String> get() = setOf(path)

    /** A choice of the machine the game boots ([MachineOption]): `machine.<id>`. */
    class Choice(val option: MachineOption) : CatalogSetting(MachineOption.SECTION, option.id)

    /** The disk in a drive beside the game's own: the `media` item of [role], named by [label]. */
    class Drive(val role: String, val label: UiText, appliesTo: (String) -> Boolean = { true }) :
        CatalogSetting(MEDIA, role, appliesTo)

    /**
     * Lines the machine types when the game starts, such as a tape's loading commands: `[field].[subfield]`,
     * an array of 1 to [maxLines] strings matching [line]. [default] gives the lines a game types
     * without any of its own (none, or a tape's load) for the machine it boots (each option's value by ID).
     */
    class Lines(field: String, subfield: String, val label: UiText, val hint: UiText, val line: Regex,
                val maxLines: Int, appliesTo: (String) -> Boolean = { true }, val default: (CatalogGame, Map<String, String>) -> List<String>) :
        CatalogSetting(field, subfield, appliesTo) {
        fun valid(lines: JSONArray?): Boolean = lines != null && lines.length() in 1..maxLines &&
            (0 until lines.length()).all { (lines.opt(it) as? String)?.matches(line) == true }

        /** The game's own lines, or null. */
        fun read(game: CatalogGame): List<String>? = game.section(field)?.optJSONArray(subfield)
            ?.let { lines -> (0 until lines.length()).map(lines::getString) }

        /** The lines the game types: its own, or its [default] for the machine [options] it boots. */
        fun value(game: CatalogGame, options: Map<String, String>): List<String> = read(game) ?: default(game, options)

        /** The lines as one row's text, and back. */
        fun text(lines: List<String>) = lines.joinToString(SEPARATOR)
        fun parse(text: String): List<String> = text.split(';').map(String::trim).filter(String::isNotEmpty)
    }

    /**
     * One string the game starts with, such as the program a disk starts: `[field].[subfield]`,
     * matching [pattern], or "" for the machine's own choice, which the row calls [empty].
     * [normalize] turns what the player types into the stored form.
     */
    class Text(field: String, subfield: String, val label: UiText, val hint: UiText, val pattern: Regex,
               val empty: UiText, val normalize: (String) -> String = String::trim,
               appliesTo: (String) -> Boolean = { true }) : CatalogSetting(field, subfield, appliesTo) {
        fun valid(value: Any?): Boolean = value is String && (value.isEmpty() || pattern.matches(value))

        /** The game's string, or "" for the machine's own choice. */
        fun value(game: CatalogGame): String = game.section(field)?.optString(subfield, "") ?: ""
    }

    /** The game's startup menus the player picks options for before it starts (`startupChoices`, [StartupAutomation]). */
    object StartupChoices : CatalogSetting(StartupAutomation.CHOICES, "")

    /**
     * Disks the frontend changes for the player when the game asks for them (`diskSwaps`,
     * [StartupAutomation]): each into one of the machine's first [drives] media slots, a disk the
     * slot [accepts].
     */
    class DiskSwaps(val drives: Int, val accepts: (drive: Int, contentId: String) -> Boolean = { _, _ -> true }) :
        CatalogSetting(StartupAutomation.SWAPS, "")

    /**
     * A setting whose row the product builds itself ([MachineActivity.customSettingRow]), named by
     * [id]: it changes [subfields] of [field], or the whole field when null.
     */
    class Custom(val id: String, field: String, val subfields: Set<String>?) :
        CatalogSetting(field, subfields?.joinToString(",") ?: "") {
        override val paths: Set<String> = subfields?.map { "$field.$it" }?.toSet() ?: setOf(field)
    }

    companion object {
        const val MEDIA = "media"
        private const val SEPARATOR = "; "

        /** The catalog fields [settings] fill, other than media: each field with its subfields. */
        fun fields(settings: List<CatalogSetting>): Map<String, Set<String>?> =
            settings.filter { it !is Drive }.groupBy { it.field }.mapValues { (_, list) ->
                if (list.any { (it is Custom && it.subfields == null) || it is StartupChoices || it is DiskSwaps }) null
                else list.flatMap { if (it is Custom) it.subfields!! else listOf(it.subfield) }.toSet()
            }

        /** The media roles [settings] fill. */
        fun roles(settings: List<CatalogSetting>): Set<String> = settings.filterIsInstance<Drive>().map { it.role }.toSet()

        /**
         * Whether [value] is a valid [field] of [settings] for a catalog of [contentId]s: startup
         * automation ([StartupChoices], [DiskSwaps]), or a section ([validSection]).
         */
        fun valid(settings: List<CatalogSetting>, contentId: Regex, field: String, value: Any): Boolean {
            settings.filterIsInstance<DiskSwaps>().firstOrNull { it.field == field }?.let { swaps ->
                return StartupAutomation.validSwaps(value, swaps.drives, contentId, swaps.accepts)
            }
            if (settings.any { it is StartupChoices && it.field == field }) return StartupAutomation.validChoices(value)
            return validSection(settings, field, value)
        }

        /** Whether [value] is a [field] section holding only valid values of [settings]. */
        fun validSection(settings: List<CatalogSetting>, field: String, value: Any): Boolean = value is JSONObject &&
            value.length() > 0 && value.keys().asSequence().all { key ->
                when (val setting = settings.firstOrNull { it.field == field && it.subfield == key }) {
                    is Choice -> setting.option.valid(value.opt(key))
                    is Lines -> setting.valid(value.optJSONArray(key))
                    is Text -> setting.valid(value.opt(key))
                    else -> false
                }
            }
    }
}
