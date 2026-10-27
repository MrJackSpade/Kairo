package com.mrjackspade.kairo.frontend

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.File

/**
 * One game's saved data of one [kind], such as a save-state slot: a directory replaced as a
 * whole, at [path] under the save root. A game's units sit beside one another as
 * `<savePath>.<kind>` ([of], Kairo#57). v1.4.0 and earlier kept them at `<game>/<kind>`, the
 * game named by its [GameKey.hashName] or, before games had keys, its content ID ([v1]).
 */
data class SaveUnit(val game: String, val kind: String, val v1: Boolean = false) {
    val path: String get() = if (v1) "$game/$kind" else "$game.$kind"

    companion object {
        fun of(game: GameKey, kind: String) = SaveUnit(game.savePath, kind)
        fun v1(game: String, kind: String) = SaveUnit(game, kind, v1 = true)
    }
}

/**
 * Where an app keeps its saves: app storage or a folder the user chose. Each unit is a
 * directory replaced as a whole, so a reader never sees half of a save.
 */
interface SaveLocation {
    /**
     * The units here of the kinds in [kinds]: its keys, as `<savePath>.<kind>` anywhere under the
     * root, and its values, as v1.4.0 and earlier kept them (`<v1 game>/<kind>` at the top).
     */
    fun units(kinds: Map<String, String>): List<SaveUnit>
    /** The games here as v1.4.0 and earlier named them: top-level `k1-` names and content IDs. */
    fun v1Games(): Set<String>
    fun exists(unit: SaveUnit): Boolean
    /** True when the unit holds no files, so there is nothing saved in it. */
    fun isEmpty(unit: SaveUnit): Boolean
    /** When the unit was last written: its newest file. */
    fun savedAt(unit: SaveUnit): Long
    /** Copies the unit into [into], which must not exist yet. */
    fun export(unit: SaveUnit, into: File)
    /** Replaces the unit with the contents of local directory [from]. */
    fun import(unit: SaveUnit, from: File)
    /** Deletes the unit, and the folders above it that it leaves empty. */
    fun delete(unit: SaveUnit)
    /** Deletes what is left of a game v1.4.0 and earlier named [game] ([v1Games]), once its units have moved. */
    fun deleteV1Game(game: String)
    /**
     * Moves unit [from] to [to], which must not exist, here: its files stay as they are, with
     * their times, and nothing is copied. A move cut short leaves [movePending] true, and moving
     * again finishes it.
     */
    fun move(from: SaveUnit, to: SaveUnit)
    /** True when a move of [from] to [to] began and did not finish. */
    fun movePending(from: SaveUnit, to: SaveUnit): Boolean

    companion object {
        private val UNSAFE_CHAR = Regex("[^A-Za-z0-9._-]")
        /** A content ID as [contentIdName] stored it. */
        private val CONTENT_ID_NAME = Regex("sha256-[a-z0-9-]+-v1_[0-9a-f]+")

        /** The directory a content ID's saves used before games had keys. */
        fun contentIdName(id: String): String = id.replace(UNSAFE_CHAR, "_").also {
            require(id.isNotBlank() && it != "." && it != "..") { "Invalid content ID" }
        }

        /** Whether a top-level directory is a game as v1.4.0 and earlier named it. */
        fun isV1Game(name: String) = GameKey.HASH_NAME.matches(name) || CONTENT_ID_NAME.matches(name)

        /** The unit a directory at [path] is, by its name: `<game>.<kind>`, or one an interrupted import left as `.old`. */
        internal fun unitAt(path: String, kinds: Set<String>): SaveUnit? {
            val name = path.removeSuffix(".old")
            return kinds.firstOrNull { name.endsWith(".$it") && name.length > it.length + 1 }
                ?.let { SaveUnit(name.removeSuffix(".$it"), it) }
        }

        /** The segments of a path under a save root; none may step out of it. */
        internal fun segments(path: String): List<String> = path.split('/').onEach(::requireSafe)

        internal fun requireSafe(name: String) =
            require(name.isNotEmpty() && name != "." && name != ".." && '/' !in name && '\u0000' !in name) {
                "Unsafe save name: $name"
            }
    }
}

/** Copies [from] to [into], which must not exist, with each file's time: a save keeps when it was saved. */
private fun copyKeepingTimes(from: File, into: File) {
    check(!into.exists()) { "Could not copy ${from.name}: ${into.name} already exists" }
    for (source in from.walkTopDown()) {
        val target = File(into, source.relativeTo(from).path)
        if (source.isDirectory) check(target.isDirectory || target.mkdirs()) { "Could not create ${target.name}" }
        else {
            source.copyTo(target)
            check(target.setLastModified(source.lastModified())) { "Could not keep the time of ${target.name}" }
        }
    }
}

/** Saves under [root] in app storage. */
class AppSaveLocation(val root: File) : SaveLocation {
    fun directory(unit: SaveUnit): File = File(root, SaveLocation.segments(unit.path).joinToString(File.separator))

    @Synchronized override fun units(kinds: Map<String, String>): List<SaveUnit> {
        val found = LinkedHashSet<SaveUnit>()
        fun walk(directory: File, prefix: String) {
            for (child in directory.listFiles().orEmpty().filter { it.isDirectory }.sortedBy { it.name }) {
                if (prefix.isEmpty() && SaveLocation.isV1Game(child.name)) {
                    kinds.values.map { SaveUnit.v1(child.name, it) }
                        .filterTo(found) { unit -> recover(unit); directory(unit).isDirectory }
                    continue
                }
                val path = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
                val unit = SaveLocation.unitAt(path, kinds.keys)
                if (unit == null) walk(child, path)
                else { recover(unit); if (directory(unit).isDirectory) found += unit }
            }
        }
        walk(root, "")
        return found.toList()
    }

    override fun v1Games(): Set<String> =
        root.listFiles().orEmpty().filter { it.isDirectory && SaveLocation.isV1Game(it.name) }.map { it.name }.toSet()

    @Synchronized override fun exists(unit: SaveUnit): Boolean { recover(unit); return directory(unit).isDirectory }

    override fun isEmpty(unit: SaveUnit): Boolean = directory(unit).walkTopDown().none { it.isFile }

    override fun savedAt(unit: SaveUnit): Long =
        directory(unit).walkTopDown().filter { it.isFile }.maxOfOrNull { it.lastModified() }
            ?: directory(unit).lastModified()

    @Synchronized override fun export(unit: SaveUnit, into: File) {
        recover(unit)
        val source = directory(unit)
        check(source.isDirectory) { "Save ${unit.path} is missing" }
        check(!into.exists()) { "Export target already exists" }
        copyKeepingTimes(source, into)
    }

    @Synchronized override fun import(unit: SaveUnit, from: File) {
        val target = directory(unit)
        recover(unit)
        val parent = target.parentFile!!
        check(parent.isDirectory || parent.mkdirs()) { "Could not create the folder for ${unit.path}" }
        val stage = File(parent, "${target.name}.moving")
        val backup = File(parent, "${target.name}.old")
        check(!stage.exists() || stage.deleteRecursively()) { "Could not clear an incomplete save" }
        copyKeepingTimes(from, stage)
        if (target.exists()) check(target.renameTo(backup)) { "Could not replace ${unit.path}" }
        if (!stage.renameTo(target)) {
            // Put the old save back. If that fails too, recover() restores it on the next access.
            if (backup.exists()) backup.renameTo(target)
            error("Could not store ${unit.path}")
        }
        check(!backup.exists() || backup.deleteRecursively()) { "Could not remove the replaced save" }
    }

    /** A rename, which is never half done. */
    @Synchronized override fun move(from: SaveUnit, to: SaveUnit) {
        val source = directory(from)
        val target = directory(to)
        check(source.isDirectory) { "Save ${from.path} is missing" }
        check(!target.exists()) { "Save ${to.path} already exists" }
        val parent = target.parentFile!!
        check(parent.isDirectory || parent.mkdirs()) { "Could not create the folder for ${to.path}" }
        check(source.renameTo(target)) { "Could not move ${from.path} to ${to.path}" }
    }

    override fun movePending(from: SaveUnit, to: SaveUnit) = false

    @Synchronized override fun deleteV1Game(game: String) {
        require(SaveLocation.isV1Game(game)) { "Not a v1 save name: $game" }
        val directory = File(root, game)
        check(!directory.exists() || directory.deleteRecursively()) { "Could not remove the earlier save folder $game" }
    }

    @Synchronized override fun delete(unit: SaveUnit) {
        val target = directory(unit)
        check(!target.exists() || target.deleteRecursively()) { "Could not delete ${unit.path}" }
        var parent = target.parentFile
        while (parent != null && parent != root && parent.list()?.isEmpty() == true) {
            check(parent.delete()) { "Could not remove the empty folder ${parent.name}" }
            parent = parent.parentFile
        }
    }

    /** An interrupted import leaves `<name>.old`. Keep the new save if it exists, else restore the old one. */
    private fun recover(unit: SaveUnit) {
        val target = directory(unit)
        val backup = File(target.parentFile, "${target.name}.old")
        if (!backup.exists()) return
        if (target.exists()) check(backup.deleteRecursively()) { "Could not remove an old save" }
        else check(backup.renameTo(target)) { "Could not restore an old save" }
    }
}

/** Saves in a folder the user chose through the storage access framework. */
class FolderSaveLocation(private val resolver: ContentResolver, val tree: Uri) : SaveLocation {
    /** A document in the folder; [modified] is when it was written, where the provider says. */
    private class Child(val uri: Uri, val directory: Boolean, val modified: Long?)

    @Synchronized override fun units(kinds: Map<String, String>): List<SaveUnit> {
        val found = LinkedHashSet<SaveUnit>()
        fun walk(directory: Uri, prefix: String) {
            for ((name, child) in children(directory).toSortedMap()) {
                if (!child.directory) continue
                if (prefix.isEmpty() && SaveLocation.isV1Game(name)) {
                    for (kind in kinds.values) {
                        recover(child.uri, kind)
                        if (children(child.uri)[kind]?.directory == true) found += SaveUnit.v1(name, kind)
                    }
                    continue
                }
                val path = if (prefix.isEmpty()) name else "$prefix/$name"
                val unit = SaveLocation.unitAt(path, kinds.keys)
                if (unit == null) walk(child.uri, path)
                else if (find(unit) != null) found += unit
            }
        }
        walk(root(), "")
        return found.toList()
    }

    @Synchronized override fun v1Games(): Set<String> =
        children(root()).filter { (name, child) -> child.directory && SaveLocation.isV1Game(name) }.keys

    @Synchronized override fun exists(unit: SaveUnit): Boolean = find(unit) != null

    @Synchronized override fun isEmpty(unit: SaveUnit): Boolean =
        !hasFiles(checkNotNull(find(unit)) { "Save ${unit.path} is missing" })

    private fun hasFiles(directory: Uri): Boolean =
        children(directory).values.any { !it.directory || hasFiles(it.uri) }

    override fun savedAt(unit: SaveUnit): Long =
        newest(checkNotNull(find(unit)) { "Save ${unit.path} is missing" }, unit)

    /** True when the unit exists and holds [file]. */
    @Synchronized fun has(unit: SaveUnit, file: String): Boolean = find(unit)?.let { children(it)[file] } != null

    /** One file of a unit, or null when the unit or file is absent. */
    @Synchronized fun read(unit: SaveUnit, file: String): ByteArray? =
        find(unit)?.let { children(it)[file] }?.let { document ->
            checkNotNull(resolver.openInputStream(document.uri)) { "Could not read $file" }.use { it.readBytes() }
        }

    @Synchronized override fun export(unit: SaveUnit, into: File) {
        val source = checkNotNull(find(unit)) { "Save ${unit.path} is missing" }
        check(!into.exists()) { "Export target already exists" }
        download(source, into)
    }

    @Synchronized override fun import(unit: SaveUnit, from: File) {
        val segments = SaveLocation.segments(unit.path)
        check(from.isDirectory) { "Nothing to store for ${unit.path}" }
        val name = segments.last()
        val parent = segments.dropLast(1).fold(root()) { directory, segment ->
            children(directory)[segment]?.uri ?: createDirectory(directory, segment)
        }
        recover(parent, name)
        val stageName = "$name.moving"
        children(parent)[stageName]?.let { check(delete(it.uri)) { "Could not clear an incomplete save" } }
        upload(from, createDirectory(parent, stageName))
        val backup = children(parent)[name]?.let {
            DocumentsContract.renameDocument(resolver, it.uri, "$name.old")
                ?: error("Could not replace ${unit.path}")
        }
        val stage = checkNotNull(children(parent)[stageName]) { "The staged save disappeared" }
        try {
            checkNotNull(DocumentsContract.renameDocument(resolver, stage.uri, name)) {
                "Could not store ${unit.path}"
            }
        } catch (error: Exception) {
            // Put the old save back. If that fails too, recover() restores it on the next access.
            backup?.let { DocumentsContract.renameDocument(resolver, it, name) }
            throw error
        }
        backup?.let { check(delete(it)) { "Saved, but could not remove the replaced save" } }
    }

    @Synchronized override fun deleteV1Game(game: String) {
        require(SaveLocation.isV1Game(game)) { "Not a v1 save name: $game" }
        children(root())[game]?.let { check(delete(it.uri)) { "Could not remove the earlier save folder $game" } }
    }

    /**
     * Renames the unit to [to]'s name where it is, then moves it into [to]'s folder: each step
     * is the provider's own, so the files keep their times. Cut short after the rename, the unit
     * waits under its new name beside where it was ([movePending]).
     */
    @Synchronized override fun move(from: SaveUnit, to: SaveUnit) {
        val source = SaveLocation.segments(from.path)
        val target = SaveLocation.segments(to.path)
        val sourceParent = directory(source.dropLast(1)) ?: error("Save ${from.path} is missing")
        val name = target.last()
        val renamed = children(sourceParent)[name]?.uri ?: run {
            val unit = children(sourceParent)[source.last()]?.uri ?: error("Save ${from.path} is missing")
            DocumentsContract.renameDocument(resolver, unit, name) ?: error("Could not move ${from.path}")
        }
        val targetParent = target.dropLast(1).fold(root()) { directory, segment ->
            children(directory)[segment]?.uri ?: createDirectory(directory, segment)
        }
        check(children(targetParent)[name] == null) { "Save ${to.path} already exists" }
        checkNotNull(DocumentsContract.moveDocument(resolver, renamed, sourceParent, targetParent)) {
            "Could not move ${from.path} to ${to.path}"
        }
    }

    @Synchronized override fun movePending(from: SaveUnit, to: SaveUnit): Boolean {
        val sourceParent = directory(SaveLocation.segments(from.path).dropLast(1)) ?: return false
        return children(sourceParent)[SaveLocation.segments(to.path).last()] != null
    }

    /** The folder at [segments] under the root, or null when it is not there. */
    private fun directory(segments: List<String>): Uri? =
        segments.fold(root() as Uri?) { directory, segment -> directory?.let { children(it)[segment]?.uri } }

    @Synchronized override fun delete(unit: SaveUnit) {
        val segments = SaveLocation.segments(unit.path)
        // The folders down to the unit, each with its parent, so the emptied ones can go too.
        val chain = ArrayList<Uri>()
        var directory = root()
        for (segment in segments.dropLast(1)) {
            directory = children(directory)[segment]?.uri ?: return
            chain += directory
        }
        children(directory)[segments.last()]?.let { check(delete(it.uri)) { "Could not delete ${unit.path}" } }
        for ((index, folder) in chain.withIndex().reversed()) {
            if (children(folder).isNotEmpty()) break
            check(delete(folder)) { "Could not remove the empty folder ${segments[index]}" }
        }
    }

    private fun find(unit: SaveUnit): Uri? {
        val segments = SaveLocation.segments(unit.path)
        var directory = root()
        for (segment in segments.dropLast(1)) directory = children(directory)[segment]?.uri ?: return null
        recover(directory, segments.last())
        return children(directory)[segments.last()]?.uri
    }

    /** An interrupted import leaves `<name>.old`. Keep the new save if it exists, else restore the old one. */
    private fun recover(parent: Uri, name: String) {
        val entries = children(parent)
        val backup = entries["$name.old"] ?: return
        if (entries.containsKey(name)) check(delete(backup.uri)) { "Could not remove an old save" }
        else checkNotNull(DocumentsContract.renameDocument(resolver, backup.uri, name)) { "Could not restore an old save" }
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
            if (document.directory) download(document.uri, target)
            else {
                checkNotNull(resolver.openInputStream(document.uri)) { "Could not read $name" }.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                // A save's time is when it was saved, not when it was copied.
                document.modified?.let(target::setLastModified)
            }
        }
    }

    private fun newest(directory: Uri, unit: SaveUnit): Long = children(directory).values.maxOfOrNull { child ->
        if (child.directory) newest(child.uri, unit) else lastModified(child.uri, unit)
    } ?: lastModified(directory, unit)

    private fun lastModified(document: Uri, unit: SaveUnit): Long =
        checkNotNull(resolver.query(document, arrayOf(Document.COLUMN_LAST_MODIFIED), null, null, null)) {
            "Could not read ${unit.path}"
        }.use { cursor ->
            check(cursor.moveToFirst() && !cursor.isNull(0) && cursor.getLong(0) > 0) {
                "The save folder does not report when ${unit.path} was saved"
            }
            cursor.getLong(0)
        }

    private fun root(): Uri = DocumentsContract.buildDocumentUriUsingTree(tree,
        DocumentsContract.getTreeDocumentId(tree))

    private fun children(parent: Uri): Map<String, Child> {
        val query = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(parent))
        return checkNotNull(resolver.query(query, arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE, Document.COLUMN_LAST_MODIFIED), null, null, null)) { "Could not read the save folder" }.use { cursor ->
            buildMap {
                while (cursor.moveToNext()) put(cursor.getString(1), Child(
                    DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0)),
                    cursor.getString(2) == Document.MIME_TYPE_DIR,
                    cursor.takeIf { !it.isNull(3) }?.getLong(3)?.takeIf { it > 0 }))
            }
        }
    }

    private fun createDirectory(parent: Uri, name: String): Uri =
        DocumentsContract.createDocument(resolver, parent, Document.MIME_TYPE_DIR, name)
            ?: error("Could not create $name in the save folder")

    private fun delete(document: Uri) = DocumentsContract.deleteDocument(resolver, document)
}
