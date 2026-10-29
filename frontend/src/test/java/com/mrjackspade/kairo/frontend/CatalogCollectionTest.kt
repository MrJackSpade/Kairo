package com.mrjackspade.kairo.frontend

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CatalogCollectionTest {
    private fun merged(member: String?, vararg records: Pair<String, String>) = CatalogFieldLayers.merge(
        LayeredGameCatalog.withMember(records.map { (name, record) -> CatalogFieldLayers.Source(name, JSONObject(record)) },
            member), { false })

    private val shipped = "Shipped catalog" to """{"title": "Two Games", "launch": "both",
        "collection": {"1": {"title": "First Game", "launch": "first"}, "2": {"title": "Second Game", "launch": "second"}}}"""

    @Test
    fun thePickedMemberSitsAboveItsCollection() {
        val result = merged("1", shipped)
        assertEquals("First Game", result.record.getString("title"))
        assertEquals("first", result.record.getString("launch"))
        assertEquals("second", merged("2", shipped).record.getString("launch"))
    }

    @Test
    fun noPickKeepsTheCollection() {
        assertEquals("Two Games", merged(null, shipped).record.getString("title"))
        assertEquals("Two Games", merged("3", shipped).record.getString("title"))
    }

    @Test
    fun aHigherLayerStillOverridesTheMember() {
        val result = merged("1", shipped, "User override" to """{"title": "My Title"}""")
        assertEquals("My Title", result.record.getString("title"))
        assertEquals("first", result.record.getString("launch"))
        assertEquals("User override", result.sourceOf("title"))
    }

    @Test
    fun eachMemberHasItsOwnKey() {
        val collection = GameKey("Games/Duke.zip")
        assertEquals(GameKey("Games/Duke.zip#1"), collection.member("1"))
        assertNotEquals(collection.member("1"), collection.member("2"))
        assertNotEquals(collection, collection.member("1"))
    }
}
