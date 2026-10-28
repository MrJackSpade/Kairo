package com.mrjackspade.kairo.frontend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CatalogUpdateArchiveTest {
    private fun archive(vararg entries: Pair<String, String>): File =
        File.createTempFile("update-", ".zip").apply {
            deleteOnExit()
            ZipOutputStream(outputStream()).use { zip ->
                for ((name, text) in entries) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(text.toByteArray())
                    zip.closeEntry()
                }
            }
        }

    private val header = "update-v1.json" to """{"schemaVersion": 1, "updateVersion": 4}"""

    @Test
    fun documentsAreReadByTheirCatalogPath() {
        CatalogUpdateArchive(archive(header, "descriptions/es.json" to """{"games": {"c64:1": "Un juego."}}""")).use {
            assertEquals("Un juego.", it.document("descriptions/es.json")!!.getJSONObject("games").getString("c64:1"))
            assertNull(it.document("descriptions/de.json"))
        }
    }

    @Test
    fun anotherFormatIsRefused() {
        assertThrows(IllegalArgumentException::class.java) {
            CatalogUpdateArchive(archive("update-v1.json" to """{"schemaVersion": 1, "updateVersion": 5}"""))
        }
    }

    @Test
    fun anUpdateWithoutARequiredDocumentIsRefused() {
        assertThrows(IllegalArgumentException::class.java) {
            CatalogUpdateArchive.validate(archive(header), "name-index-v1.json")
        }
        CatalogUpdateArchive.validate(archive(header, "name-index-v1.json" to """{"games": {}}"""), "name-index-v1.json")
    }
}
