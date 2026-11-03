package com.mrjackspade.kairo.frontend

/**
 * The library list's rows and which game is selected, apart from the views ([LibraryScreen] drives
 * it and applies the rows it asks for to its ListView), so unit tests reach it (Kairo#101).
 *
 * [entries] are the games shown, in order; [items] the list's rows: the games, under a pinned
 * Continue header and an All games header when the last-played game is pinned. [selectedIndex] is
 * the selected game in [entries]. In touch mode ListView keeps the row it was last told to select
 * for its next layout, and a data change does not move it: [requestedRow] is that row, which must
 * stay inside the rows (Kairo#99), or -1 when there are none (Kairo#100).
 */
class LibraryRows<T>(private val idOf: (T) -> String,
                     private val continueLabel: String,
                     private val allGamesLabel: String) {
    sealed interface Item
    data class Header(val label: String, val pinned: Boolean) : Item
    data class Game(val index: Int) : Item

    var entries: List<T> = emptyList()
        private set
    var items: List<Item> = emptyList()
        private set
    var selectedIndex = 0
    var requestedRow = -1
        private set

    val selected: T? get() = entries.getOrNull(selectedIndex)

    /** Whether the rows start with the pinned Continue group. */
    val pinnedShown: Boolean get() = (items.firstOrNull() as? Header)?.pinned == true

    /** The row of the game at [index] in [entries], or -1. */
    fun positionOf(index: Int) = items.indexOfFirst { it is Game && it.index == index }

    /** The game in [entries] that the row at [position] shows; null for a header or no row. */
    fun indexAt(position: Int) = (items.getOrNull(position) as? Game)?.index

    /** Records that the list was told to select the row at [position]; returns it. */
    fun request(position: Int): Int {
        requestedRow = position
        return position
    }

    /**
     * Shows [matches], with [pinned] (the last-played game, the first of [matches]) in its Continue
     * group when given. The selected game stays selected when it is still shown, else the selection
     * keeps its place inside the new games. Returns the row the list must now be told to select,
     * when the row it keeps no longer exists; null when it may keep it.
     */
    fun publish(matches: List<T>, pinned: T?): Int? {
        val selectedId = selected?.let(idOf)
        val selectedWasPinned = selectedIndex == 0 && pinnedShown
        entries = matches
        val selectedMatch = when {
            selectedWasPinned && pinned != null && idOf(pinned) == selectedId -> 0
            selectedId != null -> entries.indexOfLast { idOf(it) == selectedId }
            else -> -1
        }
        selectedIndex = if (selectedMatch >= 0) selectedMatch else
            selectedIndex.coerceIn(0, (entries.size - 1).coerceAtLeast(0))
        items = if (pinned != null && entries.isNotEmpty()) listOf(Header(continueLabel, true), Game(0)) +
            (if (entries.size > 1) listOf(Header(allGamesLabel, false)) else emptyList()) +
            (1 until entries.size).map(::Game)
            else entries.indices.map(::Game)
        return if (requestedRow >= items.size) request(positionOf(selectedIndex)) else null
    }

    /**
     * The row to select once a refreshed library is shown: the top for the first game (its group's
     * header with it), else the selected game's row; null when there are no games.
     */
    fun rowAfterRefresh(): Int? = when {
        entries.isEmpty() -> null
        selectedIndex == 0 -> request(0)
        else -> request(positionOf(selectedIndex))
    }

    /** Moves the selection [delta] games, inside the games; false when it stays where it was. */
    fun move(delta: Int): Boolean {
        if (entries.isEmpty()) return false
        val next = (selectedIndex + delta).coerceIn(0, entries.lastIndex)
        if (next == selectedIndex) return false
        selectedIndex = next
        return true
    }
}
