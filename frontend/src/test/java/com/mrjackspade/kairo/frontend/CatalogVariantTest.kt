package com.mrjackspade.kairo.frontend

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogVariantTest {
    private fun merged(variantName: String?, vararg records: Pair<String, String>) = CatalogFieldLayers.merge(
        LayeredGameCatalog.withVariants(records.map { (name, record) -> CatalogFieldLayers.Source(name, JSONObject(record)) },
            variantName), { false })

    private val shipped = "Shipped catalog" to """{"title": "Two Games", "launch": "both",
        "variants": {"first game": {"title": "First Game", "launch": "first"}}}"""

    @Test
    fun aFileNamePicksItsVariantAboveItsRecord() {
        val result = merged("first game", shipped)
        assertEquals("First Game", result.record.getString("title"))
        assertEquals("first", result.record.getString("launch"))
    }

    @Test
    fun anotherFileNameKeepsTheRecord() {
        assertEquals("Two Games", merged("second game", shipped).record.getString("title"))
        assertEquals("Two Games", merged(null, shipped).record.getString("title"))
    }

    @Test
    fun aHigherLayerStillOverridesTheVariant() {
        val result = merged("first game", shipped, "User override" to """{"title": "My Title"}""")
        assertEquals("My Title", result.record.getString("title"))
        assertEquals("first", result.record.getString("launch"))
        assertEquals("User override", result.sourceOf("title"))
    }
}
