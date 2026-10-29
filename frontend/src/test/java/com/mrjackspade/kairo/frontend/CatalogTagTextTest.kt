package com.mrjackspade.kairo.frontend

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** KairoDos#141: a language document's "tags" names a catalog's tags in that language. */
class CatalogTagTextTest {
    private val shipped = JSONObject("""{"games": {}, "tags": {"Single Player": "シングルプレイ", "Action": "アクション"}}""")
    private val update = JSONObject("""{"games": {}, "tags": {"Action": "アクション（新）"}}""")

    @Test fun aTagShowsInTheLanguage() {
        assertEquals("シングルプレイ", LayeredGameCatalog.tagText(listOf(shipped))("Single Player"))
    }

    @Test fun theUpdatesNamesComeFirst() {
        val text = LayeredGameCatalog.tagText(listOf(update, shipped))
        assertEquals("アクション（新）", text("Action"))
        assertEquals("シングルプレイ", text("Single Player"))
    }

    @Test fun aYearShowsAsItIs() {
        assertEquals("1994", LayeredGameCatalog.tagText(listOf(shipped))("1994"))
    }

    @Test fun aDocumentWithoutTagsChangesNothing() {
        assertEquals("Action", LayeredGameCatalog.tagText(listOf(JSONObject("""{"games": {}}""")))("Action"))
    }
}
