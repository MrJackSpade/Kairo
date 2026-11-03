package com.mrjackspade.kairo.frontend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The library's rows and selection (Kairo#101): whatever the rows become, the row the list keeps
 * for its next layout is one of them (Kairo#99), or none when there are no rows (Kairo#100).
 */
class LibraryRowsTest {
    private val rows = LibraryRows<String>({ it }, "CONTINUE", "ALL GAMES")
    private val games = (1..8).map { "game$it" }

    /** The list asks the adapter for its kept row at its next layout: it must exist. */
    private fun assertKeptRowExists(after: String) {
        val kept = rows.requestedRow
        if (rows.items.isEmpty()) assertEquals("kept row with no rows, after $after", -1, kept)
        else assertTrue("kept row $kept of ${rows.items.size} rows, after $after", kept in -1..rows.items.lastIndex)
    }

    private fun publish(matches: List<String>, pinned: String? = null, what: String): Int? {
        val asked = rows.publish(matches, pinned)
        if (asked != null) assertTrue("asked for row $asked of ${rows.items.size}, after $what",
            asked in rows.items.indices || (asked == -1 && rows.items.isEmpty()))
        assertKeptRowExists(what)
        return asked
    }

    private fun selectRowOf(game: String) {
        rows.selectedIndex = rows.entries.indexOf(game)
        rows.request(rows.positionOf(rows.selectedIndex))
    }

    @Test
    fun theRowsShrinkBelowTheRequestedRow() {
        publish(games, what = "the library")
        selectRowOf("game6")
        publish(listOf("game1", "game2"), what = "a search for two games")
        assertEquals("the selection inside two games", 1, rows.selectedIndex)
    }

    @Test
    fun theSelectedGameStaysSelectedWhenStillShown() {
        publish(games, what = "the library")
        selectRowOf("game6")
        publish(listOf("game2", "game6"), what = "a search keeping the selected game")
        assertEquals("game6", rows.selected)
        assertEquals(1, rows.requestedRow)
    }

    @Test
    fun theRowsBecomeEmptyThenComeBack() {
        publish(games, what = "the library")
        selectRowOf("game4")
        publish(emptyList(), what = "a search with no match")
        assertNull(rows.selected)
        assertEquals("no rows to select", -1, rows.requestedRow)
        assertNull("no row after a refresh with no games", rows.rowAfterRefresh())
        publish(games, what = "the search cleared")
        assertKeptRowExists("the search cleared")
        rows.rowAfterRefresh()
        assertKeptRowExists("a refresh after the search cleared")
    }

    /** Kairo#99: a refresh selected row 4 as a search narrowed the library to one game. */
    @Test
    fun aRefreshLandsWhileASearchNarrowsTheRows() {
        publish(games, what = "the library")
        selectRowOf("game5")
        // The refresh: results keep only games still in the library, then its selection.
        val refreshed = games - "game8"
        publish(rows.entries.filter { it in refreshed }, what = "the refresh's stale results")
        rows.rowAfterRefresh()
        assertKeptRowExists("the refresh's selection")
        // The search then answers with one game.
        publish(listOf("game1"), what = "the search narrowing to one game")
        assertEquals(0, rows.selectedIndex)
    }

    @Test
    fun thePinnedContinueRowWithOtherGames() {
        publish(listOf("game3") + games, "game3", "the library with a pinned game")
        assertEquals(listOf(LibraryRows.Header("CONTINUE", true), LibraryRows.Game(0),
            LibraryRows.Header("ALL GAMES", false)) + (1..games.size).map { LibraryRows.Game(it) }, rows.items)
        assertTrue(rows.pinnedShown)
        assertEquals("the pinned game's row", 1, rows.positionOf(0))
        assertNull("a header shows no game", rows.indexAt(0))
        assertEquals("the refresh shows the top", 0, rows.rowAfterRefresh())
        // The pinned game selected stays the pinned one, not its row in All games.
        publish(listOf("game3") + games, "game3", "the pin again")
        assertEquals(0, rows.selectedIndex)
        selectRowOf("game8")
        publish(listOf("game1"), what = "a search for one game")
        assertEquals(listOf(LibraryRows.Game(0)), rows.items)
    }

    @Test
    fun thePinnedContinueRowAlone() {
        publish(listOf("game3"), "game3", "the library with only its pinned game")
        assertEquals(listOf(LibraryRows.Header("CONTINUE", true), LibraryRows.Game(0)), rows.items)
        rows.request(1)
        publish(emptyList(), what = "a search with no match")
        assertEquals(emptyList<LibraryRows.Item>(), rows.items)
    }

    @Test
    fun theSelectionMovesInsideTheGames() {
        publish(games.take(3), what = "three games")
        assertTrue(rows.move(1))
        assertTrue(rows.move(5))
        assertEquals(2, rows.selectedIndex)
        assertTrue("already at the last game", !rows.move(1))
        publish(emptyList(), what = "no games")
        assertTrue("no games to move to", !rows.move(1))
    }
}
