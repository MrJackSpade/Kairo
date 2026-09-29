package com.mrjackspade.kairo.frontend

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File

/** A single game file supplied by an Android launcher such as ES-DE. */
data class ExternalGameFile(val uri: Uri, val name: String, val size: Long,
                            val modified: Long)

object ExternalGameIntent {
    fun hasRequest(intent: Intent): Boolean = intent.data != null || intent.hasExtra("ROM")

    fun file(intent: Intent?, resolver: ContentResolver): ExternalGameFile? {
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
        require(label.isNotBlank() && label.length <= 255) { "Game file has no usable name" }
        return ExternalGameFile(uri, label, size, modified)
    }

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
