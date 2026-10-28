package com.mrjackspade.kairo.frontend

import android.content.Context

/** Common scan feedback for library entries supplied by either emulator. */
object LibraryScanSummary {
    fun format(context: Context, entries: List<LibraryItem>, hashes: Int): String {
        val unreadable = entries.count { it.error != null }
        val hashed = context.resources.getQuantityString(R.plurals.kairo_library_hashes, hashes, hashes)
        return if (unreadable == 0) context.getString(R.string.kairo_library_summary, games(context, entries), hashed)
            else context.getString(R.string.kairo_library_summary_unreadable, games(context, entries), unreadable(context, unreadable), hashed)
    }

    /** "N games", the playable entries. */
    fun games(context: Context, entries: List<LibraryItem>): String = entries.count { it.playable }.let {
        context.resources.getQuantityString(R.plurals.kairo_library_games, it, it)
    }

    /** "N unreadable". */
    fun unreadable(context: Context, count: Int): String =
        context.resources.getQuantityString(R.plurals.kairo_library_unreadable, count, count)
}
