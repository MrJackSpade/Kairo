package com.mrjackspade.kairo

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
 * Every app runs this test against its own shipped controls catalog (catalog/parts/controls.json).
 */
class ShippedControllerMappingTest {
    private val root = File(System.getProperty("kairo.root") ?: error("kairo.root is not set"))
    private val documents = JSONObject(File(root, "catalog/parts/controls.json").readText()).getJSONObject("documents")

    @Test
    fun theSystemDefinitionBindsEveryControl() {
        val paths = documents.keys().asSequence().filter { it.endsWith(LayeredGameCatalog.SYSTEM_DOCUMENT) }.toList()
        assertEquals("one system definition", 1, paths.size)
        val bindings = documents.getJSONObject(paths.single()).getJSONObject("controller").getJSONArray("bindings")
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
}
