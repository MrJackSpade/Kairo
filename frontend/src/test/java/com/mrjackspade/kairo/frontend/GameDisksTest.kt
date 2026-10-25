package com.mrjackspade.kairo.frontend

import org.junit.Assert.assertEquals
import org.junit.Test

class GameDisksTest {
    private data class Disk(override val path: String, override val zipEntry: String? = null,
                            override val contentId: String? = "sha256-fd-v1:$path$zipEntry") : LibraryItem {
        override val id = path + (zipEntry?.let { "/$it" } ?: "")
        override val uri = "doc:$path"
        override val catalogId = contentId
        override val error: String? = null
        override val displayName = (zipEntry ?: path).substringAfterLast('/')
        override val playable = true
        override val mediaLabel = "Floppy disk"
    }

    private val tosec = "Atari ST - Games"
    private val betrayal1 = Disk("$tosec/Betrayal (1991)(Rainbird)(Disk 1 of 2).zip", "Betrayal (1991)(Rainbird)(Disk 1 of 2).st")
    private val betrayal2 = Disk("$tosec/Betrayal (1991)(Rainbird)(Disk 2 of 2).zip", "Betrayal (1991)(Rainbird)(Disk 2 of 2).st")
    private val other = Disk("$tosec/Goal! (1993)(Anco).zip", "Goal! (1993)(Anco).st")
    private val setA = Disk("Two Disks/A.zip", "Game A.st")
    private val setB = Disk("Two Disks/A.zip", "Game B.st")
    private val catalogued = Disk("Elsewhere/Data disk.st")
    private val library = listOf(betrayal1, betrayal2, other, setA, setB, catalogued) +
        (1..3000).map { Disk("$tosec/Filler $it (1990).zip", "Filler $it.st") }

    private fun own(current: Disk, mounted: List<Disk?> = emptyList(), set: Set<String> = emptySet()) =
        GameDisks.own(current, library, mounted, set, { it.catalogId }, { true })

    @Test
    fun aGameInAFolderOfThousandsHasItsOwnDisks() =
        assertEquals(listOf(betrayal1, betrayal2), own(betrayal1))

    @Test
    fun anArchiveHoldsItsGamesDisks() = assertEquals(listOf(setA, setB), own(setA))

    @Test
    fun theCatalogSetAndTheDrivesCount() =
        assertEquals(listOf(betrayal1, betrayal2, other, catalogued),
            own(betrayal1, mounted = listOf(other, null), set = setOf(catalogued.contentId!!)))

    @Test
    fun optionsNameTheArchiveAndOnlyNameItsImageWhenItHoldsSeveral() {
        val options = GameDisks.options(listOf(betrayal2, setA, setB, catalogued)).map { it.label }
        assertEquals(listOf("Betrayal (1991)(Rainbird)(Disk 2 of 2).zip", "A.zip: Game A.st", "A.zip: Game B.st", "Data disk.st"), options)
    }
}
