package com.mrjackspade.kairo

import com.mrjackspade.kairo.frontend.CatalogSetting
import com.mrjackspade.kairo.frontend.DiskImageLibrary
import com.mrjackspade.kairo.frontend.FirmwareSet
import com.mrjackspade.kairo.frontend.GuestKeyboardLayout
import com.mrjackspade.kairo.frontend.LayeredGameCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Everything an app builds as it launches that a JVM reaches, built through the code the app uses
 * (Kairo#125): two 1.4.2 builds crashed as they opened on static setup a unit test reaches
 * (KairoMSX#45: a keyboard layout built before its key data; Kairo64#52: a media format naming
 * kinds without labels). Every app runs it on its own product:
 *
 *     class C64LaunchTablesTest : LaunchTablesTest() { override fun tables() = LaunchTables(listOf(C64Media.format), C64Keys.layouts) }
 *
 * The catalog schema is the app's catalog rules (kairo-app.gradle's `kairoCatalogRules`).
 */
abstract class LaunchTablesTest {
    /** What the app's launch builds; reading each builds it, so a broken one fails its test. */
    class LaunchTables(
        val mediaFormats: List<DiskImageLibrary.Format> = emptyList(),
        val keyboards: List<GuestKeyboardLayout> = emptyList(),
        val firmware: List<FirmwareSet> = emptyList(),
        /** The product's other static tables (its keys, its machine definitions), each built as the app builds it. */
        val others: Map<String, () -> Any?> = emptyMap()
    )

    /** The app's tables, read through the code its launch uses. */
    protected abstract fun tables(): LaunchTables

    private val tables by lazy { tables() }

    private val schema by lazy {
        val name = System.getProperty("kairo.catalogRules") ?: error("kairo.catalogRules is not set")
        Class.forName(name).getField("INSTANCE").get(null) as LayeredGameCatalog.Schema
    }

    @Test
    fun theMediaFormatsBuild() {
        for (format in tables.mediaFormats) {
            assertTrue("${format.guestName}'s formats name kinds", format.kinds.isNotEmpty())
            for (kind in format.kinds.values.toSet()) {
                assertTrue("$kind has a label", kind in format.labels)
                assertTrue("$kind has a size limit", (format.maxBytes[kind] ?: 0) > 0)
            }
        }
    }

    @Test
    fun theKeyboardsBuild() {
        for (layout in tables.keyboards) {
            for (page in layout.pages) for (row in page.rows) {
                assertTrue("${layout.brand} ${page.label} has no empty row", row.isNotEmpty())
                for (key in row) {
                    assertTrue("${layout.brand} key ${key.code} has a legend", key.label.isNotEmpty())
                    assertTrue("${layout.brand} key ${key.code}'s width", key.width > 0f && key.rows >= 1)
                    assertTrue("${layout.brand} key ${key.code} has a kana legend without a kana lock",
                        key.kana == null || layout.kanaCode != null)
                    assertTrue("${layout.brand} key ${key.code} has a Num Lock legend without Num Lock",
                        key.numLockOff == null || layout.numLockCode != null)
                }
            }
            assertTrue("${layout.brand}'s chorded Shift is a Shift", layout.chordShiftCode in layout.shiftCodes)
        }
    }

    @Test
    fun theCatalogSchemaBuilds() {
        val drives = schema.settings.filterIsInstance<CatalogSetting.Drive>().map { it.role }
        assertEquals("each drive's media role once", drives.size, CatalogSetting.roles(schema.settings).size)
        assertTrue("a platform", schema.platform.isNotBlank())
        assertTrue("a catalog URL ending in /", schema.catalogUrl.startsWith("https://") && schema.catalogUrl.endsWith("/"))
        assertTrue("Esc and Enter are different keys", (schema.escapeKeys intersect schema.enterKeys).isEmpty())
        for ((field, subfields) in schema.fields) assertTrue("$field's subfields", subfields == null || subfields.isNotEmpty())
    }

    @Test
    fun theFirmwareSetsBuild() {
        for (set in tables.firmware) {
            assertTrue("${set.directory}'s parts", set.parts.isNotEmpty())
            assertEquals("${set.directory}'s part names are unique", set.parts.size, set.parts.map { it.name }.toSet().size)
        }
    }

    @Test
    fun theOtherTablesBuild() {
        for ((name, table) in tables.others) assertTrue("$name builds", table() != null)
    }
}
