package com.mrjackspade.kairo.frontend

import java.io.InputStream

/** The library screen knows only the source and identity of an imported game. */
interface LibraryItem {
    val id: String
    /** The document the entry was read from. One archive can hold several entries. */
    val uri: String
    val contentId: String?
    val path: String
    val zipEntry: String?
    val error: String?
    val displayName: String
    val playable: Boolean
    val mediaLabel: String
}

/** Metadata displayed by the shared library and game page. */
interface LibraryGame {
    val title: String
    val description: String?
    val boxArt: String?
    val preview: String?
    val tags: List<String>
}

interface LibraryCatalog {
    val installedCatalogs: InstalledCatalogs? get() = null
    fun resolve(contentId: String, fileName: String): LibraryGame
    fun hiddenFromLibrary(contentId: String): Boolean
    /**
     * The other disks of a set whose entry point is [contentId]. The library shows the
     * entry point alone while it is present; the disks stay available to disk swaps.
     */
    fun setDisks(contentId: String): Collection<String> = emptyList()
    fun openArtwork(path: String): InputStream
}

/** [folder] names the library folder in rows and buttons, such as "Choose ROM folder…". */
data class LibraryStrings(
    val productName: String,
    val appName: String,
    val folder: String = "ROM folder",
    val noFolder: String = "No ROM folder selected"
)
