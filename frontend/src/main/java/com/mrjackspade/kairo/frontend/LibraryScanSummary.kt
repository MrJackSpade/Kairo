package com.mrjackspade.kairo.frontend

/** Common scan feedback for library entries supplied by either emulator. */
object LibraryScanSummary {
    fun format(entries: List<LibraryItem>, hashes: Int): String {
        val ready = entries.count { it.playable }
        val unreadable = entries.count { it.error != null }
        return "$ready games" +
            (if (unreadable == 0) "" else " · $unreadable unreadable") +
            " · $hashes hashes this scan"
    }
}
