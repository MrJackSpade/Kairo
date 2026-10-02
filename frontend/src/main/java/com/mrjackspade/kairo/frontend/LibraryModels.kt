package com.mrjackspade.kairo.frontend

import java.io.InputStream

/** The library screen knows only the source and identity of an imported game. */
interface LibraryItem {
    val id: String
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
    fun openArtwork(path: String): InputStream
}

data class LibraryStrings(
    val productName: String,
    val selectFolder: String = "Select ROM folder",
    val folderHint: String = "Choose where disk images and ZIP games are stored",
    val noFolder: String = "No ROM folder selected"
)
