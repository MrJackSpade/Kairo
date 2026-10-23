package com.mrjackspade.kairo.frontend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FolderListingsTest {
    private val folder: File = Files.createTempDirectory("folder-listings").toFile()
    private val listings = FolderListings(File(folder, "folder-listings-v1.json"))
    private val tree = "content://com.android.externalstorage.documents/tree/FAF7-34B4%3ADOS"

    @Test
    fun aWrittenListingReadsBackTheSame() {
        listings.write(tree, mapOf("FAF7-34B4:DOS" to FolderListings.Listing(1792720818530, listOf(
            FolderListings.Child("FAF7-34B4:DOS/Doom (1993).zip", "Doom (1993).zip", false, 2_408_812, 1792720000000),
            FolderListings.Child("FAF7-34B4:DOS/Extras", "Extras", true, -1, 0)))))
        val read = listings.read(tree).getValue("FAF7-34B4:DOS")
        assertEquals(1792720818530, read.modified)
        assertEquals(listOf("Doom (1993).zip", "Extras"), read.children.map { it.name })
        assertEquals(listOf(false, true), read.children.map { it.folder })
        assertEquals(listOf(2_408_812L, -1L), read.children.map { it.size })
        assertEquals("FAF7-34B4:DOS/Doom (1993).zip", read.children[0].id)
        assertEquals(1792720000000, read.children[0].modified)
    }

    @Test
    fun anotherTreeHasNoListings() {
        listings.write(tree, mapOf("FAF7-34B4:DOS" to FolderListings.Listing(1, emptyList())))
        assertTrue(listings.read("content://com.android.externalstorage.documents/tree/FAF7-34B4%3APC88").isEmpty())
    }

    @Test
    fun nothingWrittenYetHasNoListings() = assertTrue(listings.read(tree).isEmpty())

    @Test
    fun aNewScanReplacesTheOldListings() {
        listings.write(tree, mapOf("a" to FolderListings.Listing(1, emptyList()), "b" to FolderListings.Listing(2, emptyList())))
        listings.write(tree, mapOf("a" to FolderListings.Listing(3, emptyList())))
        assertEquals(mapOf("a" to 3L), listings.read(tree).mapValues { it.value.modified })
    }
}
