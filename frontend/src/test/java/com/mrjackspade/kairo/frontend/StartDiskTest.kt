package com.mrjackspade.kairo.frontend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.InputStream

class StartDiskTest {
    private fun disk(zipEntry: String, label: String, archive: String = "Games/Set.zip") = DiskImageEntry(
        id = "$archive/$zipEntry", uri = "doc:$archive", path = archive, zipEntry = zipEntry, sourceSize = 1,
        sourceModified = 1, imageSize = 1, imageCrc = 1, contentId = "sha256-x-v1:$archive/$zipEntry", mediaLabel = label)

    /** A catalog that knows [known], lists [sets] as their entry points' set disks and hides [hidden]. */
    private class Catalog(val known: Set<LibraryItem> = emptySet(), val sets: Map<LibraryItem, List<LibraryItem>> = emptyMap(),
                          val hidden: Set<LibraryItem> = emptySet()) : LibraryCatalog {
        override fun resolve(item: LibraryItem): LibraryGame = error("not used")
        override fun hiddenFromLibrary(item: LibraryItem) = item in hidden
        override fun matchId(item: LibraryItem) = item.contentId
        override fun setDisks(item: LibraryItem) = sets[item].orEmpty().mapNotNull { it.contentId }
        override fun knows(item: LibraryItem) = item in known
        override fun openArtwork(path: String): InputStream = error("not used")
    }

    private val cd = disk("Game.cue", "CD-ROM")
    private val userDisk = disk("Game [UserDisk].hdm", "Floppy disk")
    private val hardDisk = disk("Game [HDD].h0", "Hard disk")

    @Test
    fun oneDiskStarts() = assertEquals(cd, startDisk(listOf(cd), Catalog()) { null })

    @Test
    fun aSetStartsFromItsEntryPoint() =
        assertEquals(hardDisk, startDisk(listOf(cd, hardDisk), Catalog(sets = mapOf(hardDisk to listOf(cd)))) { null })

    @Test
    fun aHiddenSupportDiskDoesNotStart() =
        assertEquals(cd, startDisk(listOf(cd, userDisk), Catalog(hidden = setOf(userDisk))) { null })

    @Test
    fun theDiskTheCatalogKnowsStartsBeforeAnUnknownOne() =
        assertEquals(cd, startDisk(listOf(cd, userDisk), Catalog(known = setOf(cd))) { null })

    @Test
    fun theProductPicksWhenTheCatalogDoesNot() =
        assertEquals(userDisk, startDisk(listOf(cd, userDisk), Catalog()) { it.last() })

    @Test
    fun theyChooseWhenNothingSettlesIt() = assertNull(startDisk(listOf(cd, userDisk), Catalog()) { null })

    @Test
    fun aCompanionIsTheOneOfItsKindBesideTheDisk() {
        val entries = listOf(cd, userDisk, hardDisk, disk("Other.cue", "CD-ROM", "Games/Other.zip"))
        assertEquals(cd, companionFor(null, hardDisk, entries, "CD") { it.mediaLabel == "CD-ROM" })
        assertNull(companionFor(null, cd, entries, "hard disk") { it.mediaLabel == "Floppy disk" && it.id.endsWith(".d77") })
    }

    @Test
    fun aCompanionTheCatalogNamesMustBeThere() {
        assertThrows(IllegalStateException::class.java) {
            companionFor("sha256-x-v1:missing", cd, listOf(cd, userDisk), "boot floppy") { it.mediaLabel == "Floppy disk" }
        }
    }
}
