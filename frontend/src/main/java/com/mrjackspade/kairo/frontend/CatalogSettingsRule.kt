package com.mrjackspade.kairo.frontend

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * AGENTS.md's rule that every setting a catalog entry can give a game is also a per-game setting
 * the player can change, as checks an app's unit test runs (shared/app/test). An app names its
 * rules in its build (`kairoCatalogRules`): its [LayeredGameCatalog.Schema], or for a catalog of
 * another design its own [CatalogSettingsRule].
 */
interface CatalogSettingsRule {
    /** What breaks the rule in the app whose project root is [root]: one line each. */
    fun problems(root: File): List<String>

    companion object {
        /** The rules an app names by class: its schema's, or its own. */
        fun of(rules: Any): CatalogSettingsRule = when (rules) {
            is CatalogSettingsRule -> rules
            is LayeredGameCatalog.Schema -> LayeredCatalogSettings(rules)
            else -> error("${rules::class.qualifiedName} is neither a catalog schema nor catalog settings rules")
        }
    }
}

/**
 * The rule for a [LayeredGameCatalog]: its product fields are its [LayeredGameCatalog.Schema.settings],
 * each a row of the game's settings, and the shipped catalog (catalog/core-v3.json) gives games only
 * those, the common settings every game's settings have rows for, and fields that describe the game.
 */
class LayeredCatalogSettings(private val schema: LayeredGameCatalog.Schema) : CatalogSettingsRule {
    private val settingPaths = schema.settings.flatMap { it.paths }.toSet()
    private val roles = CatalogSetting.roles(schema.settings)

    /** Settings every game's settings page has rows for (KairoActivity: Button mapping, Screen tap, ...). */
    private val common = setOf("controller.profile", "controller.bindings", "controller.defaults",
        "input.mode", "input.secondary") + if (schema.directTouch) setOf("input.touch") else emptySet()

    override fun problems(root: File): List<String> {
        val problems = mutableListOf<String>()
        for ((field, subfields) in schema.fields) {
            if (subfields == null) { if (field !in settingPaths) problems += "$field: no game setting" }
            else subfields.filter { "$field.$it" !in settingPaths }.forEach { problems += "$field.$it: no game setting" }
        }
        val catalog = File(root, "catalog/core-v3.json")
        require(catalog.isFile) { "${catalog.path} is missing" }
        val document = JSONObject(catalog.readText())
        records(document.getJSONObject("games")).forEach { (id, record) -> problems += recordProblems(id, record) }
        records(document.getJSONObject("nameIndex").getJSONObject("games"))
            .forEach { (id, record) -> problems += recordProblems(id, record) }
        // One line per kind of problem, with how many records have it and the first one.
        return problems.groupBy { it.substringAfter(": ") }.map { (problem, all) ->
            "${all.first().substringBefore(": ")}: $problem" + if (all.size > 1) " (and ${all.size - 1} more)" else ""
        }
    }

    private fun records(games: JSONObject) = games.keys().asSequence().map { it to games.getJSONObject(it) }

    private fun recordProblems(id: String, record: JSONObject): List<String> =
        record.keys().asSequence().flatMap { field ->
            val value = record.get(field)
            when {
                field in DESCRIBES -> emptyList()
                field == CatalogSetting.MEDIA -> mediaProblems(id, value)
                field in setOf("controller", "input") ->
                    (value as JSONObject).keys().asSequence().map { "$field.$it" }.filter { it !in common }
                        .map { "$id: $it has no game setting" }.toList()
                field !in schema.fields -> listOf("$id: $field has no game setting")
                !schema.validField(field, value) -> listOf("$id: $field holds a value its setting cannot show: $value")
                else -> emptyList()
            }
        }.toList()

    private fun mediaProblems(id: String, value: Any): List<String> {
        val items = value as JSONArray
        return (0 until items.length()).map(items::getJSONObject).mapNotNull { item ->
            val role = item.optString("role")
            when {
                role !in roles -> "$id: media role $role has no game setting"
                !schema.validMedia(role, item.optString("contentId")) -> "$id: media $role holds a disk its drive cannot take"
                else -> null
            }
        }
    }

    companion object {
        /** Fields that say what a game is, not how it runs: which game, its disks, its name and pictures. */
        private val DESCRIBES = setOf("game", "setDisks", "title", "description", "descriptions", "aliases", "heart", "hidden", "artwork")
    }
}
