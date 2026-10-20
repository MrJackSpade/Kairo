package com.mrjackspade.kairo.frontend

import java.security.MessageDigest

/**
 * A game's identity: its path relative to the library folder, exactly as the provider reports
 * it, or a document URI for a game opened from outside that folder. Saves and settings follow
 * the key, so editing a game's files keeps them; renaming or moving the game makes a new game.
 */
@JvmInline value class GameKey(val path: String) {
    /**
     * The key as a directory or preference name. Paths may differ only in case, hold characters
     * a folder name cannot, or be long; a hash stores every key exactly and alike.
     */
    val storageName: String get() = STORAGE_PREFIX + MessageDigest.getInstance("SHA-256")
        .digest(path.toByteArray(Charsets.UTF_8)).take(16).joinToString("") { "%02x".format(it) }

    companion object {
        const val STORAGE_PREFIX = "k1-"

        /** An entry inside an archive: the archive's path, then the entry's path within it. */
        fun inArchive(archive: String, entry: String) = GameKey("$archive/$entry")
    }
}
