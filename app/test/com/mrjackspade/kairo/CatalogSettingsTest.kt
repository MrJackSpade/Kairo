package com.mrjackspade.kairo

import com.mrjackspade.kairo.frontend.CatalogSettingsRule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * AGENTS.md: every setting a catalog entry can give a game is also a per-game setting the player
 * can change. Every app runs this test against its own catalog; kairo-app.gradle names the app's
 * rules (`kairoCatalogRules`) and its project root.
 */
class CatalogSettingsTest {
    @Test
    fun everyCatalogSettingIsAGameSetting() {
        val name = System.getProperty("kairo.catalogRules") ?: error("kairo.catalogRules is not set")
        val rules = Class.forName(name).getField("INSTANCE").get(null) ?: error("$name has no instance")
        val root = File(System.getProperty("kairo.root") ?: error("kairo.root is not set"))
        assertEquals("", CatalogSettingsRule.of(rules).problems(root).joinToString("\n"))
    }
}
