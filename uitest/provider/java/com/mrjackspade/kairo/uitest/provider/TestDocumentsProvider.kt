package com.mrjackspade.kairo.uitest.provider

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import com.mrjackspade.kairo.frontend.UiTestDocuments
import java.io.File
import java.io.FileNotFoundException

/**
 * The UI test build's document provider: the files the tests place in the app's own
 * [UiTestDocuments.DIRECTORY], served as the system picker's providers serve a folder, so the app
 * reads its games and BIOS through the same document-tree code a player's pick uses. A document's
 * id is its path below that folder. Only UI test builds include it (shared/gradle/kairo-uitest.gradle).
 */
class TestDocumentsProvider : DocumentsProvider() {
    private val root: File get() = File(requireNotNull(context).filesDir, UiTestDocuments.DIRECTORY)

    override fun onCreate() = true

    private fun file(id: String): File {
        val file = File(root, id).canonicalFile
        if (file != root.canonicalFile && !file.path.startsWith(root.canonicalPath + File.separator))
            throw FileNotFoundException("$id is outside the test documents")
        if (!file.exists()) throw FileNotFoundException("No test document $id")
        return file
    }

    private fun id(file: File) = file.canonicalFile.relativeTo(root.canonicalFile).invariantSeparatorsPath

    private fun add(cursor: MatrixCursor, file: File) {
        val directory = file.isDirectory
        cursor.newRow()
            .add(Document.COLUMN_DOCUMENT_ID, id(file))
            .add(Document.COLUMN_DISPLAY_NAME, file.name)
            .add(Document.COLUMN_MIME_TYPE, if (directory) Document.MIME_TYPE_DIR else mime(file.name))
            .add(Document.COLUMN_SIZE, if (directory) null else file.length())
            .add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
            .add(Document.COLUMN_FLAGS, if (directory) Document.FLAG_DIR_SUPPORTS_CREATE else
                Document.FLAG_SUPPORTS_WRITE or Document.FLAG_SUPPORTS_DELETE)
    }

    private fun mime(name: String) = MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"

    override fun queryRoots(projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: ROOT_COLUMNS).apply {
            newRow().add(Root.COLUMN_ROOT_ID, "uitest").add(Root.COLUMN_DOCUMENT_ID, "")
                .add(Root.COLUMN_TITLE, "UI test documents").add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_IS_CHILD)
        }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: DOCUMENT_COLUMNS).apply { add(this, file(documentId)) }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor =
        MatrixCursor(projection ?: DOCUMENT_COLUMNS).apply {
            file(parentDocumentId).listFiles().orEmpty().sortedBy { it.name }.forEach { add(this, it) }
        }

    override fun isChildDocument(parentDocumentId: String, documentId: String) =
        parentDocumentId.isEmpty() || documentId.startsWith("$parentDocumentId/")

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor =
        ParcelFileDescriptor.open(file(documentId), ParcelFileDescriptor.parseMode(mode))

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val parent = file(parentDocumentId)
        val created = File(parent, displayName)
        if (mimeType == Document.MIME_TYPE_DIR) check(created.mkdir()) { "Could not create $created" }
        else check(created.createNewFile()) { "Could not create $created" }
        return id(created)
    }

    override fun deleteDocument(documentId: String) {
        check(file(documentId).deleteRecursively()) { "Could not delete $documentId" }
    }

    private companion object {
        val ROOT_COLUMNS = arrayOf(Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_FLAGS)
        val DOCUMENT_COLUMNS = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS)
    }
}
