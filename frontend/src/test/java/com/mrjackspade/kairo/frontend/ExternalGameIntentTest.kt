package com.mrjackspade.kairo.frontend

import org.junit.Assert.assertEquals
import org.junit.Test

class ExternalGameIntentTest {
    private data class Game(val key: String, val contentId: String?)

    private val pirates = Game("Games/Pirates.d88", "sha256-fd-v1:pirates")
    private val copyA = Game("Games/Copies/Ys.d88", "sha256-fd-v1:ys")
    private val copyB = Game("Games/More/Ys.d88", "sha256-fd-v1:ys")
    private val library = listOf(pirates, copyA, copyB, Game("Games/Unread.d88", null))

    private fun resolve(vararg inspected: Game) = ExternalGameIntent.libraryGames(inspected.toList(), library) { it.contentId }

    @Test
    fun aFileHoldingALibraryGamesContentOpensAsThatGame() {
        assertEquals(listOf(pirates), resolve(Game("content://frontend/Pirates.d88", "sha256-fd-v1:pirates")))
    }

    @Test
    fun aFileNoLibraryGameHoldsStaysItsOwnGame() {
        val outside = Game("content://frontend/New.d88", "sha256-fd-v1:new")
        assertEquals(listOf(outside), resolve(outside))
    }

    @Test
    fun contentTwoLibraryGamesHoldNamesNeither() {
        val outside = Game("content://frontend/Ys.d88", "sha256-fd-v1:ys")
        assertEquals(listOf(outside), resolve(outside))
    }

    @Test
    fun anEntryWithoutContentIdStaysItsOwnGame() {
        val outside = Game("content://frontend/Broken.d88", null)
        assertEquals(listOf(outside), resolve(outside))
    }

    @Test
    fun eachDiskOfAnArchiveIsResolvedOnItsOwn() {
        val disk1 = Game("content://frontend/Set.zip/Pirates.d88", "sha256-fd-v1:pirates")
        val disk2 = Game("content://frontend/Set.zip/Data.d88", "sha256-fd-v1:data")
        assertEquals(listOf(pirates, disk2), resolve(disk1, disk2))
    }
}
