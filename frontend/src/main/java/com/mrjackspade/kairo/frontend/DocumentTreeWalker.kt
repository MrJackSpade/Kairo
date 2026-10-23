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
 * the same breadth-first order however the queries finish. With [FolderListings] and a depth that
 * does not relist, a folder whose last-modified time is unchanged keeps its remembered listing
 * instead (ScanDepth).
 */
class DocumentTreeWalker(private val resolver: ContentResolver) {
    data class FileEntry(val uri: Uri, val path: String, val size: Long, val modified: Long)
    data class FolderError(val uri: Uri, val path: String, val message: String)
    data class Result(val files: List<FileEntry>, val folders: List<FolderError>)

    private class Folder(val id: String, val path: String)
    private class Listing(val files: List<FileEntry>, val folders: List<Folder>, val error: FolderError?,
                          val remembered: FolderListings.Listing?)

    fun scan(tree: Uri, cancelled: AtomicBoolean, include: (String) -> Boolean, progress: (String) -> Unit,
             memory: FolderListings? = null, depth: ScanDepth = ScanDepth.EVERY_FOLDER): Result {
        val known = if (memory == null || depth.relist) emptyMap() else try {
            memory.read(tree.toString())
        } catch (failure: Exception) {
            // The listings only save time, as the library cache does: unreadable ones are reported,
            // then every folder is listed and they are replaced.
            android.util.Log.e("KairoLibrary", "Folder listings unreadable; listing every folder", failure)
            emptyMap()
        }
        val files = ArrayList<FileEntry>()
        val errors = ArrayList<FolderError>()
        val read = HashMap<String, FolderListings.Listing>()
        val visited = AtomicInteger()
        var level = listOf(Folder(DocumentsContract.getTreeDocumentId(tree), ""))
        var listed = 0
        while (level.isNotEmpty()) {
            if (cancelled.get()) throw CancellationException("Cancelled")
            val listings = ParallelScanWork.map(level, cancelled, LISTING_WORKERS) { _, folder ->
                list(tree, folder, cancelled, include, visited, known[folder.id])
            }
            val next = ArrayList<Folder>()
            for ((folder, listing) in level.zip(listings)) {
                files.addAll(listing.files)
                next.addAll(listing.folders)
                listing.remembered?.let { read[folder.id] = it }
                listing.error?.let {
                    errors.add(it)
                    progress("Skipping unreadable folder: ${it.path}")
                }
            }
            listed += level.size
            progress("Checking the games folder: $listed folders, ${files.size} media files")
            level = next
        }
        memory?.write(tree.toString(), read)
        return Result(files, errors)
    }

    /**
     * One folder's media files and subfolders: from [known], its remembered listing, when its
     * last-modified time still matches, or else from its provider.
     */
    private fun list(tree: Uri, folder: Folder, cancelled: AtomicBoolean, include: (String) -> Boolean,
                     visited: AtomicInteger, known: FolderListings.Listing?): Listing {
        require(folder.path.count { it == '/' } <= MAX_DEPTH) { "Game folder is too deep" }
        val remembered = try {
            val modified = modified(tree, folder.id)
            if (known != null && modified != 0L && known.modified == modified) known
            else FolderListings.Listing(modified, children(tree, folder.id, cancelled))
        } catch (cancel: CancellationException) { throw cancel }
        catch (failure: Exception) {
            // The games folder itself must be readable; an unreadable folder inside it is reported.
            if (folder.path.isEmpty()) throw failure
            return Listing(emptyList(), emptyList(), FolderError(
                DocumentsContract.buildDocumentUriUsingTree(tree, folder.id), folder.path,
                failure.message ?: "provider error"), null)
        }
        val files = ArrayList<FileEntry>()
        val folders = ArrayList<Folder>()
        for (child in remembered.children) {
            if (cancelled.get()) throw CancellationException("Cancelled")
            require(visited.incrementAndGet() <= MAX_DOCUMENTS) { "Game folder has too many files" }
            val path = if (folder.path.isEmpty()) child.name else "${folder.path}/${child.name}"
            if (child.folder) folders.add(Folder(child.id, path))
            else if (include(child.name))
                files.add(FileEntry(DocumentsContract.buildDocumentUriUsingTree(tree, child.id), path, child.size, child.modified))
        }
        return Listing(files, folders, null, remembered)
    }

    /** A folder's own last-modified time, one row from its provider; 0 when the provider gives none. */
    private fun modified(tree: Uri, id: String): Long =
        resolver.query(DocumentsContract.buildDocumentUriUsingTree(tree, id), MODIFIED, null, null, null)?.use { cursor ->
            check(cursor.moveToFirst()) { "Unable to read game folder" }
            if (cursor.isNull(0)) 0 else cursor.getLong(0)
        } ?: error("Unable to read game folder")

    /** Every child of a folder, as its provider lists them. */
    private fun children(tree: Uri, id: String, cancelled: AtomicBoolean): List<FolderListings.Child> {
        val children = ArrayList<FolderListings.Child>()
        resolver.query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, id), PROJECTION, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            val timeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            while (cursor.moveToNext()) {
                if (cancelled.get()) throw CancellationException("Cancelled")
                val childId = cursor.getString(idColumn) ?: continue
                val name = cursor.getString(nameColumn) ?: continue
                children.add(FolderListings.Child(childId, name,
                    cursor.getString(mimeColumn) == DocumentsContract.Document.MIME_TYPE_DIR,
                    if (cursor.isNull(sizeColumn)) -1 else cursor.getLong(sizeColumn),
                    if (cursor.isNull(timeColumn)) 0 else cursor.getLong(timeColumn)))
            }
        } ?: error("Unable to read game folder")
        return children
    }

    companion object {
        private const val MAX_DEPTH = 24
        private const val MAX_DOCUMENTS = 50_000
        /** Folder listings wait on the provider, not the CPU, so more run at once than scan workers. */
        private const val LISTING_WORKERS = 8
        private val MODIFIED = arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        private val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
    }
}
