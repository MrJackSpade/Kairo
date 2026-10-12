package com.mrjackspade.kairo.frontend

/** Shared validation for app-specific packaged artwork namespaces. */
object ArtworkOverridePath {
    fun valid(path: String, prefix: String, asciiOnly: Boolean = false,
              minLength: Int = 1): Boolean =
        path.length in minLength..256 && path.startsWith(prefix) &&
            !path.contains("..") && !path.contains('\\') && !path.startsWith('/') &&
            (!asciiOnly || path.matches(Regex("[a-zA-Z0-9/._-]+")))
}
