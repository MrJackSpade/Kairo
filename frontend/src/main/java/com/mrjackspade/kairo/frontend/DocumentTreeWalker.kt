package com.mrjackspade.kairo.frontend

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Bounded, read-only traversal of a user-selected Android document tree. Each folder is one query
 * to its document provider, so the folders of each level are listed in parallel; the result is in
 * the same breadth-first order however the queries finish.
 */
class DocumentTreeWalker(private val resolver: ContentResolver) {
    data class FileEntry(val uri: Uri, val path: String, val size: Long, val modified: Long)
    data class FolderError(val uri: Uri, val path: String, val message: String)
    data class Result(val files: List<FileEntry>, val folders: List<FolderError>)

    private class Folder(val id: String, val path: String)
    private class Listing(val files: List<FileEntry>, val folders: List<Folder>, val error: FolderError?)

    fun scan(tree: Uri, cancelled: AtomicBoolean, include: (String) -> Boolean,
             progress: (String) -> Unit): Result {
        val files = ArrayList<FileEntry>()
        val errors = ArrayList<FolderError>()
        val visited = AtomicInteger()
        var level = listOf(Folder(DocumentsContract.getTreeDocumentId(tree), ""))
        var listed = 0
        while (level.isNotEmpty()) {
            if (cancelled.get()) throw CancellationException("Cancelled")
            val listings = ParallelScanWork.map(level, cancelled, LISTING_WORKERS) { _, folder ->
                list(tree, folder, cancelled, include, visited)
            }
            val next = ArrayList<Folder>()
            for (listing in listings) {
                files.addAll(listing.files)
                next.addAll(listing.folders)
                listing.error?.let {
                    errors.add(it)
                    progress("Skipping unreadable folder: ${it.path}")
                }
            }
            listed += level.size
            progress("Checking the games folder: $listed folders, ${files.size} media files")
            level = next
        }
        return Result(files, errors)
    }

    private fun list(tree: Uri, folder: Folder, cancelled: AtomicBoolean, include: (String) -> Boolean,
                     visited: AtomicInteger): Listing {
        require(folder.path.count { it == '/' } <= MAX_DEPTH) { "Game folder is too deep" }
        val files = ArrayList<FileEntry>()
        val folders = ArrayList<Folder>()
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, folder.id)
        try {
            resolver.query(children, PROJECTION, null, null, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                val timeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                while (cursor.moveToNext()) {
                    if (cancelled.get()) throw CancellationException("Cancelled")
                    require(visited.incrementAndGet() <= MAX_DOCUMENTS) { "Game folder has too many files" }
                    val id = cursor.getString(idColumn) ?: continue
                    val name = cursor.getString(nameColumn) ?: continue
                    val path = if (folder.path.isEmpty()) name else "${folder.path}/$name"
                    if (cursor.getString(mimeColumn) == DocumentsContract.Document.MIME_TYPE_DIR) {
                        folders.add(Folder(id, path))
                    } else if (include(name)) {
                        files.add(FileEntry(
                            DocumentsContract.buildDocumentUriUsingTree(tree, id), path,
                            if (cursor.isNull(sizeColumn)) -1 else cursor.getLong(sizeColumn),
                            if (cursor.isNull(timeColumn)) 0 else cursor.getLong(timeColumn)))
                    }
                }
            } ?: error("Unable to read game folder")
        } catch (cancel: CancellationException) { throw cancel }
        catch (failure: Exception) {
            // The games folder itself must be readable; an unreadable folder inside it is reported.
            if (folder.path.isEmpty()) throw failure
            return Listing(emptyList(), emptyList(), FolderError(
                DocumentsContract.buildDocumentUriUsingTree(tree, folder.id), folder.path,
                failure.message ?: "provider error"))
        }
        return Listing(files, folders, null)
    }

    companion object {
        private const val MAX_DEPTH = 24
        private const val MAX_DOCUMENTS = 50_000
        /** Folder listings wait on the provider, not the CPU, so more run at once than scan workers. */
        private const val LISTING_WORKERS = 8
        private val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
    }
}
