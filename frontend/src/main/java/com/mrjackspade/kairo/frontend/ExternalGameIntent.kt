package com.mrjackspade.kairo.frontend

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File

/**
 * A single game file supplied by an Android launcher such as ES-DE. [key] is the [GameKey] path
 * its game has (ExternalGameIntent.key); an archive's games are members of it.
 */
data class ExternalGameFile(val uri: Uri, val name: String, val size: Long,
                            val modified: Long, val key: String)

object ExternalGameIntent {
    fun hasRequest(intent: Intent): Boolean = intent.data != null || intent.hasExtra("ROM")

    /** The file [intent] names; [tree] is the library folder, which its key is relative to. */
    fun file(intent: Intent?, context: Context, tree: Uri?): ExternalGameFile? {
        val resolver = context.contentResolver
        if (intent == null || intent.action !in listOf(Intent.ACTION_VIEW,
                Intent.ACTION_MAIN, null)) return null
        val supplied = intent.data ?: intent.getStringExtra("ROM")?.let(Uri::parse)
            ?: return null
        val uri = when (supplied.scheme?.lowercase()) {
            "content", "file" -> supplied
            null -> supplied.path?.takeIf { it.startsWith('/') }?.let { Uri.fromFile(File(it)) }
            else -> null
        } ?: return null
        var name: String? = null
        var size = -1L
        var modified = 0L
        if (uri.scheme == "content") runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }
                        ?.let { name = cursor.getString(it) }
                    cursor.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 &&
                        !cursor.isNull(it) }?.let { size = cursor.getLong(it) }
                }
            }
        }
        if (uri.scheme == "content") runCatching {
            resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED),
                null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) modified = cursor.getLong(0)
            }
        }
        if (uri.scheme == "file") {
            val source = File(uri.path ?: return null)
            if (source.isFile) { size = source.length(); modified = source.lastModified() }
        }
        val label = (name ?: uri.lastPathSegment ?: "")
            .substringAfterLast('/').substringAfterLast('\\').substringAfterLast(':')
        require(label.isNotBlank() && label.length <= 255) { context.getString(R.string.kairo_external_no_name) }
        return ExternalGameFile(uri, label, size, modified, key(uri, tree))
    }

    /**
     * The key of a game a frontend sent (Kairo#56): its path relative to the library folder
     * [tree], with `..` steps for a file beside the folder, so it is the game the library lists
     * and keeps its saves on every device. A file on another volume, or a URI that does not say
     * where its file is, is keyed by the URI.
     */
    fun key(uri: Uri, tree: Uri?): String {
        if (tree == null || tree.authority != EXTERNAL_STORAGE) return uri.toString()
        val document = when (uri.scheme) {
            "file" -> uri.path?.let(::documentIdOf)
            "content" -> if (uri.authority == EXTERNAL_STORAGE) documentId(uri) else null
            else -> null
        } ?: return uri.toString()
        return relativePath(document, DocumentsContract.getTreeDocumentId(tree)) ?: uri.toString()
    }

    private fun documentId(uri: Uri): String? = when {
        uri.pathSegments.getOrNull(2) == "document" || uri.pathSegments.firstOrNull() == "document" ->
            DocumentsContract.getDocumentId(uri)
        else -> null
    }

    /**
     * The external storage provider's document ID for a file path on shared storage:
     * `/storage/emulated/0/Games/a.hdi` is `primary:Games/a.hdi`, `/storage/1234-ABCD/a.hdi` is
     * `1234-ABCD:a.hdi`. Null for any other path, or one with `.` or `..` steps.
     */
    fun documentIdOf(path: String): String? {
        val segments = path.split('/').filter { it.isNotEmpty() }
        if (!path.startsWith('/') || segments.any { it == "." || it == ".." }) return null
        val (volume, rest) = when {
            segments.size > 3 && segments[0] == "storage" && segments[1] == "emulated" &&
                segments[2].all(Char::isDigit) -> "primary" to segments.drop(3)
            segments.size > 3 && segments[0] == "storage" && segments[1] == "self" && segments[2] == "primary" ->
                "primary" to segments.drop(3)
            segments.size > 1 && segments[0] == "sdcard" -> "primary" to segments.drop(1)
            segments.size > 2 && segments[0] == "mnt" && segments[1] == "sdcard" -> "primary" to segments.drop(2)
            segments.size > 2 && segments[0] == "storage" && segments[1] != "emulated" && segments[1] != "self" ->
                segments[1] to segments.drop(2)
            else -> return null
        }
        return "$volume:${rest.joinToString("/")}"
    }

    /**
     * [document]'s path relative to the folder [tree], both external storage document IDs, with
     * `..` steps up out of the folder; null when it is on another volume, or is the folder itself.
     */
    fun relativePath(document: String, tree: String): String? {
        val volume = document.substringBefore(':', "")
        if (volume.isEmpty() || volume != tree.substringBefore(':', "")) return null
        val file = document.substringAfter(':').split('/').filter { it.isNotEmpty() }
        val folder = tree.substringAfter(':').split('/').filter { it.isNotEmpty() }
        var common = 0
        while (common < file.size && common < folder.size && file[common] == folder[common]) common++
        if (common == file.size) return null
        return (List(folder.size - common) { ".." } + file.drop(common)).joinToString("/")
    }

    private const val EXTERNAL_STORAGE = "com.android.externalstorage.documents"

    /** A tree URI and a document URI can name the same SAF file with different strings. */
    fun sameDocument(first: Uri, second: Uri): Boolean {
        if (first == second) return true
        if (first.scheme != "content" || second.scheme != "content" ||
            first.authority != second.authority) return false
        val firstId = runCatching { DocumentsContract.getDocumentId(first) }.getOrNull()
        val secondId = runCatching { DocumentsContract.getDocumentId(second) }.getOrNull()
        return firstId != null && firstId == secondId
    }
}
