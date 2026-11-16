package com.mrjackspade.kairo.frontend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class LibraryIndexTest {
    private fun member(archive: String, member: String, contentId: String? = "sha256-fd-v1:$archive/$member",
                       catalogId: String? = contentId) = DiskImageEntry(
        id = "$archive/$member", uri = "doc:$archive", path = archive, zipEntry = member, sourceSize = 1, sourceModified = 1,
        imageSize = 1, imageCrc = 1, contentId = contentId, catalogId = catalogId)

    private fun loose(path: String, contentId: String? = "sha256-fd-v1:$path") = DiskImageEntry(
        id = path, uri = "doc:$path", path = path, zipEntry = null, sourceSize = 1, sourceModified = 1,
        imageSize = 1, imageCrc = 1, contentId = contentId)

    private val library = listOf(
        member("Games/A.zip", "A1.st"), member("Games/A.zip", "A2.st"), member("Games/B.zip", "B1.st"),
        member("Other/A.zip", "A1.st"), loose("Games/C1.st"), loose("Games/C2.st"), loose("Games/Sub/D.st"),
        loose("Top.st"), loose("Root2.st"), member("Games/E.zip", "E.st", catalogId = "sha256-fd-v1:catalog-E"),
        loose("Games/C1.st", contentId = null))

    /** What a scan of the whole library keeps: the entries sameSource keeps, and those with a linked ID. */
    private fun scanned(entry: LibraryItem, ids: Set<String>) =
        library.filter { sameSource(entry, it) || it.contentId in ids || it.catalogId in ids }

    @Test
    fun everyEntryFindsWhatAScanFinds() {
        val index = LibraryIndex.of(library, null)
        val linked = setOf("sha256-fd-v1:Games/B.zip/B1.st", "sha256-fd-v1:catalog-E", "sha256-fd-v1:Top.st", "missing")
        for (entry in library) {
            assertEquals(entry.id, scanned(entry, emptySet()), index.sameSourceOrIds(entry, emptySet()))
            assertEquals(entry.id, scanned(entry, linked), index.sameSourceOrIds(entry, linked))
        }
    }

    @Test
    fun aDocumentGivesItsEntries() {
        val index = LibraryIndex.of(library, null)
        assertEquals(library.subList(0, 2), index.fromDocument("doc:Games/A.zip"))
        assertEquals(emptyList<DiskImageEntry>(), index.fromDocument("doc:Nowhere.zip"))
    }

    @Test
    fun theSameListKeepsItsIndex() {
        val index = LibraryIndex.of(library, null)
        assertSame(index, LibraryIndex.of(library, index))
        assertEquals(library.drop(1), LibraryIndex.of(library.drop(1), index).entries)
    }
}
