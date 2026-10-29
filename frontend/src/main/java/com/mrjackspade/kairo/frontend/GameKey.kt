package com.mrjackspade.kairo.frontend

import java.security.MessageDigest

/**
 * A game's identity: its path relative to the library folder, exactly as the provider reports
 * it; `..` steps for a game beside the folder (Kairo#56); or the URI a frontend sent for a game it
 * could not place. Saves and settings follow the key, so editing a game's files keeps them;
 * renaming or moving the game makes a new game.
 */
@JvmInline value class GameKey(val path: String) {
    /**
     * Where the game's data is stored, relative to a save root: [path] with each segment made a
     * safe file name ([SavePath]). A game's saves sit beside one another under it, as
     * `<savePath>.<kind>` (Kairo#57).
     */
    val savePath: String get() = SavePath.of(path)

    /** The game [member] of the collection this key names, which keeps saves and settings of its own. */
    fun member(member: String) = GameKey("$path#$member")

    /**
     * A short ASCII name for the game, a hash of [path]: v1.4.0 and earlier stored all of a
     * game's data under it, which [GamePathMigration] moves to [savePath]. Nothing is named by it
     * since v1.4.1: it only finds what an earlier version left.
     */
    val hashName: String get() = HASH_PREFIX + MessageDigest.getInstance("SHA-256")
        .digest(path.toByteArray(Charsets.UTF_8)).copyOf(16).toHex()

    companion object {
        const val HASH_PREFIX = "k1-"
        /** A [hashName]. */
        val HASH_NAME = Regex("k1-[0-9a-f]{32}")

        /** An entry inside an archive: the archive's path, then the entry's path within it. */
        fun inArchive(archive: String, entry: String) = GameKey("$archive/$entry")
    }
}

/**
 * A game key's path as a path under a save root. Each segment is percent-encoded where it could
 * not be a file name on every storage a save folder may be on, or would step out of the root:
 * `%`, `\ : * ? " < > |` and control characters; a `.` or `..` segment; a trailing dot or space,
 * which FAT drops. An empty segment, as in a URI's `//`, is `%`. A library path needs none of this
 * and stays as it is; the encoding never maps two paths to one.
 */
object SavePath {
    private const val RESERVED = "%\\:*?\"<>|"

    fun of(path: String): String = path.split('/').joinToString("/", transform = ::segment)

    private fun segment(segment: String): String = when (segment) {
        "" -> "%"
        "." -> "%2E"
        ".." -> "%2E%2E"
        else -> buildString {
            segment.forEachIndexed { index, char ->
                val last = index == segment.length - 1
                if (char in RESERVED || char.code < 0x20 || char.code == 0x7F || (last && (char == '.' || char == ' ')))
                    append('%').append("%02X".format(char.code))
                else append(char)
            }
        }
    }
}
