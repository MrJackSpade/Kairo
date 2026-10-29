package com.mrjackspade.kairo.frontend

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kairo#61: a game's own title in the device's language, from the catalog's language documents. */
class LocalTitleTest {
    private val shipped = JSONObject("""{"games": {}, "titles": {"pc98:1": "東方封魔録 ～ the Story of Eastern Wonderland", "pc98:2": "旧題"}}""")
    private val update = JSONObject("""{"games": {}, "titles": {"pc98:2": "新題"}}""")

    @Test fun aGameShowsItsTitleInTheLanguage() {
        assertEquals("東方封魔録 ～ the Story of Eastern Wonderland", LayeredGameCatalog.localTitle(listOf(shipped), "pc98:1"))
    }

    @Test fun theUpdatesTitleComesFirst() {
        assertEquals("新題", LayeredGameCatalog.localTitle(listOf(update, shipped), "pc98:2"))
        assertEquals("東方封魔録 ～ the Story of Eastern Wonderland", LayeredGameCatalog.localTitle(listOf(update, shipped), "pc98:1"))
    }

    @Test fun aGameWithoutOneHasNone() {
        assertNull(LayeredGameCatalog.localTitle(listOf(update, shipped), "pc98:3"))
        assertNull(LayeredGameCatalog.localTitle(listOf(JSONObject("""{"games": {}}""")), "pc98:1"))
    }

    @Test fun itReplacesOnlyTheShippedCatalogsTitles() {
        assertTrue(LayeredGameCatalog.localTitleShows("Shipped catalog"))
        assertTrue(LayeredGameCatalog.localTitleShows("Updated catalog"))
        assertFalse(LayeredGameCatalog.localTitleShows("User override"))
        assertFalse(LayeredGameCatalog.localTitleShows("User catalog"))
        assertFalse(LayeredGameCatalog.localTitleShows("My installed catalog"))
        assertFalse(LayeredGameCatalog.localTitleShows(null))
    }
}
