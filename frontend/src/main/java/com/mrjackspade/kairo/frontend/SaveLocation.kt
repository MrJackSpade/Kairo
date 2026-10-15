package com.mrjackspade.kairo.frontend

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.File

/** One game's saved data of one kind, such as a save-state slot: `<game>/<name>/`. */
data class SaveUnit(val game: String, val name: String)

/**
 * Where an app keeps its saves: app storage or a folder the user chose. Each unit is a
 * directory replaced as a whole, so a reader never sees half of a save.
 */
interface SaveLocation {
    /** The units present here whose name is one of [names]. */
    fun units(names: Set<String>): List<SaveUnit>
    fun exists(unit: SaveUnit): Boolean
    /** True when the unit holds no files, so there is nothing saved in it. */
    fun isEmpty(unit: SaveUnit): Boolean
    /** When the unit was last written: its newest file. */
    fun savedAt(unit: SaveUnit): Long
    /** Copies the unit into [into], which must not exist yet. */
    fun export(unit: SaveUnit, into: File)
    /** Replaces the unit with the contents of local directory [from]. */
    fun import(unit: SaveUnit, from: File)
    fun delete(unit: SaveUnit)

    companion object {
        private val SAFE = Regex("[A-Za-z0-9._-]+")
        private val UNSAFE_CHAR = Regex("[^A-Za-z0-9._-]")

        /** The directory name used for a game ID in every location. */
        fun gameName(id: String): String = id.replace(UNSAFE_CHAR, "_").also {
            require(id.isNotBlank() && it != "." && it != "..") { "Invalid game ID" }
        }

        internal fun requireSafe(name: String) =
            require(name.matches(SAFE) && name != "." && name != "..") { "Unsafe save name: $name" }
    }
}

/** Saves under [root] in app storage. */
class AppSaveLocation(val root: File) : SaveLocation {
    fun directory(unit: SaveUnit): File {
        SaveLocation.requireSafe(unit.game)
        SaveLocation.requireSafe(unit.name)
        return File(File(root, unit.game), unit.name)
    }

    @Synchronized override fun units(names: Set<String>): List<SaveUnit> =
        root.listFiles().orEmpty().filter { it.isDirectory }.flatMap { game ->
            names.map { SaveUnit(game.name, it) }.filter { unit -> recover(unit); directory(unit).isDirectory }
        }

    @Synchronized override fun exists(unit: SaveUnit): Boolean { recover(unit); return directory(unit).isDirectory }

    override fun isEmpty(unit: SaveUnit): Boolean = directory(unit).walkTopDown().none { it.isFile }

    override fun savedAt(unit: SaveUnit): Long =
        directory(unit).walkTopDown().filter { it.isFile }.maxOfOrNull { it.lastModified() }
            ?: directory(unit).lastModified()

    @Synchronized override fun export(unit: SaveUnit, into: File) {
        recover(unit)
        val source = directory(unit)
        check(source.isDirectory) { "Save ${unit.game}/${unit.name} is missing" }
        check(!into.exists()) { "Export target already exists" }
        check(source.copyRecursively(into)) { "Could not copy ${unit.game}/${unit.name}" }
    }

    @Synchronized override fun import(unit: SaveUnit, from: File) {
        val target = directory(unit)
        recover(unit)
        val stage = File(target.parentFile, "${unit.name}.moving")
        val backup = File(target.parentFile, "${unit.name}.old")
        check(!stage.exists() || stage.deleteRecursively()) { "Could not clear an incomplete save" }
        check(from.copyRecursively(stage)) { "Could not copy ${unit.game}/${unit.name}" }
        if (target.exists()) check(target.renameTo(backup)) { "Could not replace ${unit.game}/${unit.name}" }
        if (!stage.renameTo(target)) {
            // Put the old save back. If that fails too, recover() restores it on the next access.
            if (backup.exists()) backup.renameTo(target)
            error("Could not store ${unit.game}/${unit.name}")
        }
        check(!backup.exists() || backup.deleteRecursively()) { "Could not remove the replaced save" }
    }

    @Synchronized override fun delete(unit: SaveUnit) {
        val target = directory(unit)
        check(!target.exists() || target.deleteRecursively()) { "Could not delete ${unit.game}/${unit.name}" }
        target.parentFile?.takeIf { it.list()?.isEmpty() == true }?.delete()
    }

    /** An interrupted import leaves `<name>.old`. Keep the new save if it exists, else restore the old one. */
    private fun recover(unit: SaveUnit) {
        val target = directory(unit)
        val backup = File(target.parentFile, "${unit.name}.old")
        if (!backup.exists()) return
        if (target.exists()) check(backup.deleteRecursively()) { "Could not remove an old save" }
        else check(backup.renameTo(target)) { "Could not restore an old save" }
    }
}

/** Saves in a folder the user chose through the storage access framework. */
class FolderSaveLocation(private val resolver: ContentResolver, val tree: Uri) : SaveLocation {
    @Synchronized override fun units(names: Set<String>): List<SaveUnit> =
        children(root()).filter { (_, uri) -> isDirectory(uri) }.flatMap { (game, uri) ->
            names.mapNotNull { name ->
                SaveUnit(game, name).takeIf { recover(uri, name); children(uri)[name] != null }
            }
        }

    @Synchronized override fun exists(unit: SaveUnit): Boolean = find(unit) != null

    @Synchronized override fun isEmpty(unit: SaveUnit): Boolean =
        !hasFiles(checkNotNull(find(unit)) { "Save ${unit.game}/${unit.name} is missing" })

    private fun hasFiles(directory: Uri): Boolean =
        children(directory).values.any { !isDirectory(it) || hasFiles(it) }

    override fun savedAt(unit: SaveUnit): Long =
        newest(checkNotNull(find(unit)) { "Save ${unit.game}/${unit.name} is missing" }, unit)

    /** True when the unit exists and holds [file]. */
    @Synchronized fun has(unit: SaveUnit, file: String): Boolean = find(unit)?.let { children(it)[file] } != null

    /** One file of a unit, or null when the unit or file is absent. */
    @Synchronized fun read(unit: SaveUnit, file: String): ByteArray? =
        find(unit)?.let { children(it)[file] }?.let { document ->
            checkNotNull(resolver.openInputStream(document)) { "Could not read $file" }.use { it.readBytes() }
        }

    @Synchronized override fun export(unit: SaveUnit, into: File) {
        val source = checkNotNull(find(unit)) { "Save ${unit.game}/${unit.name} is missing" }
        check(!into.exists()) { "Export target already exists" }
        download(source, into)
    }

    @Synchronized override fun import(unit: SaveUnit, from: File) {
        SaveLocation.requireSafe(unit.game)
        SaveLocation.requireSafe(unit.name)
        check(from.isDirectory) { "Nothing to store for ${unit.game}/${unit.name}" }
        val root = root()
        val game = children(root)[unit.game] ?: createDirectory(root, unit.game)
        recover(game, unit.name)
        val stageName = "${unit.name}.moving"
        children(game)[stageName]?.let { check(delete(it)) { "Could not clear an incomplete save" } }
        upload(from, createDirectory(game, stageName))
        val backup = children(game)[unit.name]?.let {
            DocumentsContract.renameDocument(resolver, it, "${unit.name}.old")
                ?: error("Could not replace ${unit.game}/${unit.name}")
        }
        val stage = checkNotNull(children(game)[stageName]) { "The staged save disappeared" }
        try {
            checkNotNull(DocumentsContract.renameDocument(resolver, stage, unit.name)) {
                "Could not store ${unit.game}/${unit.name}"
            }
        } catch (error: Exception) {
            // Put the old save back. If that fails too, recover() restores it on the next access.
            backup?.let { DocumentsContract.renameDocument(resolver, it, unit.name) }
            throw error
        }
        backup?.let { check(delete(it)) { "Saved, but could not remove the replaced save" } }
    }

    @Synchronized override fun delete(unit: SaveUnit) {
        val game = children(root())[unit.game] ?: return
        children(game)[unit.name]?.let { check(delete(it)) { "Could not delete ${unit.game}/${unit.name}" } }
        if (children(game).isEmpty()) check(delete(game)) { "Could not remove the empty folder ${unit.game}" }
    }

    private fun find(unit: SaveUnit): Uri? {
        val game = children(root())[unit.game] ?: return null
        recover(game, unit.name)
        return children(game)[unit.name]
    }

    /** An interrupted import leaves `<name>.old`. Keep the new save if it exists, else restore the old one. */
    private fun recover(game: Uri, name: String) {
        val entries = children(game)
        val backup = entries["$name.old"] ?: return
        if (entries.containsKey(name)) check(delete(backup)) { "Could not remove an old save" }
        else checkNotNull(DocumentsContract.renameDocument(resolver, backup, name)) { "Could not restore an old save" }
    }

    private fun upload(from: File, into: Uri) {
        checkNotNull(from.listFiles()) { "Could not read ${from.name}" }.forEach { file ->
            SaveLocation.requireSafe(file.name)
            if (file.isDirectory) upload(file, createDirectory(into, file.name))
            else {
                val document = DocumentsContract.createDocument(resolver, into, "application/octet-stream",
                    file.name) ?: error("Could not create ${file.name}")
                file.inputStream().use { input ->
                    checkNotNull(resolver.openOutputStream(document, "w")) { "Could not write ${file.name}" }
                        .use { output -> input.copyTo(output) }
                }
            }
        }
    }

    private fun download(from: Uri, into: File) {
        check(into.mkdirs()) { "Could not create ${into.name}" }
        children(from).forEach { (name, document) ->
            SaveLocation.requireSafe(name)
            val target = File(into, name)
            if (isDirectory(document)) download(document, target)
            else checkNotNull(resolver.openInputStream(document)) { "Could not read $name" }.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    private fun newest(directory: Uri, unit: SaveUnit): Long = children(directory).values.maxOfOrNull { child ->
        if (isDirectory(child)) newest(child, unit) else lastModified(child, unit)
    } ?: lastModified(directory, unit)

    private fun lastModified(document: Uri, unit: SaveUnit): Long =
        checkNotNull(resolver.query(document, arrayOf(Document.COLUMN_LAST_MODIFIED), null, null, null)) {
            "Could not read ${unit.game}/${unit.name}"
        }.use { cursor ->
            check(cursor.moveToFirst() && !cursor.isNull(0) && cursor.getLong(0) > 0) {
                "The save folder does not report when ${unit.game}/${unit.name} was saved"
            }
            cursor.getLong(0)
        }

    private fun root(): Uri = DocumentsContract.buildDocumentUriUsingTree(tree,
        DocumentsContract.getTreeDocumentId(tree))

    private fun children(parent: Uri): Map<String, Uri> {
        val query = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(parent))
        return checkNotNull(resolver.query(query, arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME),
            null, null, null)) { "Could not read the save folder" }.use { cursor ->
            buildMap {
                while (cursor.moveToNext()) put(cursor.getString(1),
                    DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0)))
            }
        }
    }

    private fun isDirectory(document: Uri) = resolver.getType(document) == Document.MIME_TYPE_DIR

    private fun createDirectory(parent: Uri, name: String): Uri =
        DocumentsContract.createDocument(resolver, parent, Document.MIME_TYPE_DIR, name)
            ?: error("Could not create $name in the save folder")

    private fun delete(document: Uri) = DocumentsContract.deleteDocument(resolver, document)
}
