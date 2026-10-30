package com.mrjackspade.kairo

import com.mrjackspade.kairo.frontend.ControllerLayout
import com.mrjackspade.kairo.frontend.LayeredGameCatalog
import com.mrjackspade.kairo.frontend.PhysicalControllerBindings
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * What we ship maps every control: the system's catalog definition binds each one (the lowest
 * layer of every mapping, ControllerDefaults.cascade), and no shipped mapping holds an explicit
 * null, which would leave a control dead. Only a player's own saved layer may unbind a control.
 * No shipped mapping gives START Esc and SELECT Enter: not the system's, and not any game's over it.
 * Every app runs this test against its own shipped controls catalog (catalog/parts/controls.json)
 * and its catalog rules (`kairoCatalogRules`), which name its Esc and Enter keys.
 */
class ShippedControllerMappingTest {
    private val root = File(System.getProperty("kairo.root") ?: error("kairo.root is not set"))
    private val documents = JSONObject(File(root, "catalog/parts/controls.json").readText()).getJSONObject("documents")
    private val schema = (System.getProperty("kairo.catalogRules") ?: error("kairo.catalogRules is not set")).let { name ->
        Class.forName(name).getField("INSTANCE").get(null) as LayeredGameCatalog.Schema
    }

    private fun systemBindings(): JSONArray {
        val paths = documents.keys().asSequence().filter { it.endsWith(LayeredGameCatalog.SYSTEM_DOCUMENT) }.toList()
        assertEquals("one system definition", 1, paths.size)
        return documents.getJSONObject(paths.single()).getJSONObject("controller").getJSONArray("bindings")
    }

    @Test
    fun theSystemDefinitionBindsEveryControl() {
        val bindings = systemBindings()
        val bound = (0 until bindings.length()).map { bindings.getJSONObject(it).getString("input") }.toSet()
        assertEquals("", PhysicalControllerBindings.controls.map { "virtual:$it" }.filter { it !in bound }.joinToString())
    }

    @Test
    fun nothingShippedIsAnExplicitNull() {
        val nulls = mutableListOf<String>()
        fun walk(value: Any?, path: String) {
            when (value) {
                is JSONObject -> {
                    if (value.has("unbound")) nulls += "$path: ${value.optString("input")}"
                    value.keys().forEach { walk(value.get(it), "$path/$it") }
                }
                is JSONArray -> (0 until value.length()).forEach { walk(value.get(it), "$path[$it]") }
            }
        }
        documents.keys().forEach { walk(documents.get(it), it) }
        assertEquals("", nulls.take(10).joinToString("\n") + if (nulls.size > 10) "\n(and ${nulls.size - 10} more)" else "")
    }

    @Test
    fun noShippedMappingGivesStartEscAndSelectEnter() {
        val system = bindingsByInput(systemBindings())
        val swapped = mutableListOf<String>()
        if (swaps(system)) swapped += LayeredGameCatalog.SYSTEM_DOCUMENT
        // Every game's catalog controls, in each layout, over the system's (a layout without its own
        // falls back to the one without sticks, as CatalogGame.controllerLayer does).
        fun walk(value: Any?, path: String) {
            when (value) {
                is JSONObject -> {
                    val defaults = value.optJSONObject("controller")?.optJSONObject("defaults")
                    if (defaults != null) for (layout in ControllerLayout.entries) {
                        val own = defaults.optJSONArray(layout.key) ?: defaults.optJSONArray(ControllerLayout.WITHOUT_STICKS.key)
                            ?: continue
                        if (swaps(system + bindingsByInput(own))) swapped += "$path (${layout.key})"
                    }
                    value.keys().forEach { walk(value.get(it), "$path/$it") }
                }
                is JSONArray -> (0 until value.length()).forEach { walk(value.get(it), "$path[$it]") }
            }
        }
        documents.keys().forEach { walk(documents.get(it), it) }
        assertEquals("", swapped.take(10).joinToString("\n") + if (swapped.size > 10) "\n(and ${swapped.size - 10} more)" else "")
    }

    private fun bindingsByInput(bindings: JSONArray): Map<String, JSONObject> =
        (0 until bindings.length()).map(bindings::getJSONObject).associateBy { it.getString("input") }

    private fun swaps(mapping: Map<String, JSONObject>) =
        sends(mapping["virtual:start"], schema.escapeKeys) && sends(mapping["virtual:select"], schema.enterKeys)

    private fun sends(binding: JSONObject?, keys: Set<Int>): Boolean {
        val codes = binding?.optJSONArray("keys") ?: return false
        return (0 until codes.length()).any { codes.getInt(it) in keys }
    }
}
