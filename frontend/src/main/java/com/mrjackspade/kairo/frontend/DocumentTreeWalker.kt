package com.mrjackspade.kairo.frontend

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/** Bounded, read-only traversal of a user-selected Android document tree. */
class DocumentTreeWalker(private val resolver: ContentResolver) {
    data class FileEntry(val uri: Uri, val path: String, val size: Long, val modified: Long)
    data class FolderError(val uri: Uri, val path: String, val message: String)
    data class Result(val files: List<FileEntry>, val folders: List<FolderError>)

    fun scan(tree: Uri, cancelled: AtomicBoolean, include: (String) -> Boolean,
             progress: (String) -> Unit): Result {
        val files = ArrayList<FileEntry>()
        val errors = ArrayList<FolderError>()
        val queue = ArrayDeque<Pair<String, String>>()
        queue.add(DocumentsContract.getTreeDocumentId(tree) to "")
        var visited = 0
        while (queue.isNotEmpty()) {
            if (cancelled.get()) throw CancellationException("Cancelled")
            val (parentId, parentPath) = queue.removeFirst()
            require(parentPath.count { it == '/' } <= MAX_DEPTH) { "Game folder is too deep" }
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
            try {
                resolver.query(children, PROJECTION, null, null, null)?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                    val timeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    while (cursor.moveToNext()) {
                        if (cancelled.get()) throw CancellationException("Cancelled")
                        require(++visited <= MAX_DOCUMENTS) { "Game folder has too many files" }
                        val id = cursor.getString(idColumn) ?: continue
                        val name = cursor.getString(nameColumn) ?: continue
                        val path = if (parentPath.isEmpty()) name else "$parentPath/$name"
                        if (cursor.getString(mimeColumn) == DocumentsContract.Document.MIME_TYPE_DIR) {
                            queue.add(id to path)
                        } else if (include(name)) {
                            files.add(FileEntry(
                                DocumentsContract.buildDocumentUriUsingTree(tree, id), path,
                                if (cursor.isNull(sizeColumn)) -1 else cursor.getLong(sizeColumn),
                                if (cursor.isNull(timeColumn)) 0 else cursor.getLong(timeColumn)))
                            if (files.size % 20 == 0) progress("Found ${files.size} media files")
                        }
                    }
                } ?: error("Unable to read game folder")
            } catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) {
                if (parentPath.isEmpty()) throw failure
                errors.add(FolderError(
                    DocumentsContract.buildDocumentUriUsingTree(tree, parentId), parentPath,
                    failure.message ?: "provider error"))
                progress("Skipping unreadable folder: $parentPath")
            }
        }
        return Result(files, errors)
    }

    companion object {
        private const val MAX_DEPTH = 24
        private const val MAX_DOCUMENTS = 50_000
        private val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
    }
}
