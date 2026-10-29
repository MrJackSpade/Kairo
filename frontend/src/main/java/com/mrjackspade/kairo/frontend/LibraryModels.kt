package com.mrjackspade.kairo.frontend

import java.io.InputStream

/** The library screen knows only the source and identity of an imported game. */
interface LibraryItem {
    /** Saves and settings follow it; a collection member has its own ([GameKey.member]). */
    val key: GameKey get() = GameKey(id).let { key -> member?.let(key::member) ?: key }
    /**
     * The game the player picked from a collection, one medium holding several games
     * ([LibraryCatalog.collection]); null for any other game and for the collection itself.
     */
    val member: String? get() = null
    /** The game's [GameKey] path; saves and settings follow it. */
    val id: String
    /** The document the entry was read from. One archive can hold several entries. */
    val uri: String
    val contentId: String?
    /** The content this game had when its key was first scanned. Its catalog record still applies after an edit. */
    val catalogId: String?
    val path: String
    val zipEntry: String?
    val error: String?
    val displayName: String
    val playable: Boolean
    /** What kind of game file this is, shown as a chip on the game page, such as Floppy disk. */
    val mediaLabel: UiText
    /** False for a file the scan remembers but that holds no game, such as an archive of manuals; never listed. */
    val holdsGame: Boolean get() = true
}

/** A game of a collection: its [id] in the collection's record and its title. */
data class CollectionMember(val id: String, val title: String)

/** Metadata displayed by the shared library and game page. */
interface LibraryGame {
    val title: String
    val description: String?
    val boxArt: String?
    val preview: String?
    val tags: List<String>
    /** The titles library search finds the game by. */
    val searchTitles: List<String> get() = listOf(title)
}

interface LibraryCatalog {
    val installedCatalogs: InstalledCatalogs? get() = null
    fun resolve(item: LibraryItem): LibraryGame
    fun hiddenFromLibrary(item: LibraryItem): Boolean
    /**
     * The content ID of [item]'s catalog record: its current content when the catalog knows
     * it, else the content its key was first scanned with, so an edited game keeps its record.
     */
    fun matchId(item: LibraryItem): String?
    /**
     * The other disks of a set whose entry point is [item], by catalog content ID. The library
     * shows the entry point alone while it is present; the disks stay available to disk swaps.
     */
    fun setDisks(item: LibraryItem): Collection<String> = emptyList()
    /**
     * The games of [item]'s collection, one medium holding several games, in the order the player
     * picks from; empty for any other game and for a member already picked ([LibraryItem.member]).
     */
    fun collection(item: LibraryItem): List<CollectionMember> = emptyList()
    /** Whether a catalog has a record for [item], so it is a game the catalog knows. */
    fun knows(item: LibraryItem): Boolean = false
    fun openArtwork(path: String): InputStream
}

/** [folder] names the library folder in rows and buttons, such as "Choose games folder…". */
data class LibraryStrings(
    val productName: String,
    val appName: String,
    val folder: UiText = UiText.res(R.string.kairo_games_folder),
    val noFolder: UiText = UiText.res(R.string.kairo_no_games_folder)
)
