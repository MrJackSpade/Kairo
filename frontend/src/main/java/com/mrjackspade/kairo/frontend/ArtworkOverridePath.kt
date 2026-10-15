package com.mrjackspade.kairo.frontend

/** Path guard for app-specific packaged artwork namespaces, checked where a path is opened or copied. */
object ArtworkOverridePath {
    private val ASCII_PATH = Regex("[a-zA-Z0-9/._-]+")

    fun valid(path: String, prefix: String, asciiOnly: Boolean = false,
              minLength: Int = 1): Boolean =
        path.length in minLength..256 && path.startsWith(prefix) &&
            !path.contains("..") && !path.contains('\\') && !path.startsWith('/') &&
            (!asciiOnly || path.matches(ASCII_PATH))
}
