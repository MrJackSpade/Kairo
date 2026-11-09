package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.OutputStream

/**
 * A blank disk a player makes a new game on, in a format and size the app's system used.
 * [unavailable] says why it cannot be made now (its row is then disabled with that reason), such
 * as firmware the player has not imported; null when it can.
 */
sealed class BlankDisk(val id: String, val label: UiText, val unavailable: () -> UiText? = { null }) {
    /**
     * An image written to the games folder at once: [extension] is its file extension; [write]
     * writes the whole blank image, as the system's own tools would find a new, unformatted disk;
     * [size] is how many bytes that is, for the Creating page's progress (0 when not known).
     */
    class Image(id: String, label: UiText, val extension: String, val size: Long = 0, val write: (OutputStream) -> Unit) :
        BlankDisk(id, label)

    /**
     * An empty drive the new game starts on at once ([start], with its name). The app writes the
     * game to the games folder once it holds a file, at [path] (for its name) in the games folder;
     * an empty one leaves nothing behind.
     */
    class Drive(id: String, label: UiText, val path: (name: String) -> String, val start: (name: String) -> Unit) : BlankDisk(id, label)

    /**
     * A new entry written to the games folder at once as a folder of [files], each at its path in
     * the folder (such as `86box.cfg` and `nvr/486ap4.nvr`).
     */
    class Folder(id: String, label: UiText, val files: List<File>,
                 unavailable: () -> UiText? = { null }) : BlankDisk(id, label, unavailable) {
        init {
            require(files.isNotEmpty() && files.all { file ->
                file.path.split('/').let { parts -> parts.all { it.isNotEmpty() && it != "." && it != ".." } }
            }) { "$id has a file outside its folder" }
        }
    }

    /**
     * One of a [Folder]'s files: its [path] in the folder, what [write] puts in it, and [size], how
     * many bytes that is, for the Creating page's progress (0 for a small file, or one not known).
     */
    class File(val path: String, val size: Long = 0, val write: (OutputStream) -> Unit)
}

/**
 * Writes a new game: what an app's New game workflow made ([NewGameFlow], Kairo#181), while its
 * Creating page shows how far it is (Kairo#184). An image is written to a folder of the game's name
 * in the games folder, `<name>/<name>.<extension>`, a folder of files as `<name>/...`, and the
 * library finds it there as a game of its own, set up as any game the catalog does not know: its
 * drives (installer or system disks from anywhere, through Disks), its startup, its settings. An
 * empty drive writes nothing: it starts at once ([BlankDisk.Drive]).
 */
object CreateGameFlow {
    /** The longest name a new game's folder and disk take. */
    private const val MAX_NAME = 128

    /**
     * How far a write is: [percent] of its bytes (null while the sizes are not known) and the
     * [file] being written.
     */
    class Progress(val percent: Int?, val file: String)

    /**
     * Counts a write of files of [sizes] into [Progress], telling [progress] each time the percent or
     * the file changes: [start] as each file begins, [written] as its bytes go. A file counts no more
     * than its own size (a small file given as 0 counts nothing); with no sizes known (all 0) there is
     * no percent.
     */
    internal class Tally(sizes: List<Long>, private val progress: (Progress) -> Unit) {
        private val total = sizes.sum()
        private var before = 0L
        private var file = ""
        private var size = 0L
        private var reported: Progress? = null

        fun start(name: String, bytes: Long) {
            before += size
            file = name
            size = bytes
            written(0)
        }

        fun written(bytes: Long) {
            val percent = if (total > 0) ((before + bytes.coerceAtMost(size)) * 100 / total).toInt() else null
            if (reported?.let { it.percent == percent && it.file == file } == true) return
            reported = Progress(percent, file).also(progress)
        }
    }

    /** A name a folder and file can take on every storage provider. */
    fun usable(name: String) = name.isNotEmpty() && name.length <= MAX_NAME && name != "." && name != ".." &&
        name.none { it in "/\\:*?\"<>|" || it < ' ' }

    /**
     * Writes [blank] as [name] under [tree]'s root, on the calling thread (not the UI's), telling
     * [progress] as it goes; returns the game's path in the games folder. Throws with the reason, in
     * the player's words, when it cannot: [name] already there ([folder] is what the games folder is
     * called), or storage that refused a file.
     */
    fun write(activity: Activity, tree: Uri, blank: BlankDisk, name: String, folder: String, progress: (Progress) -> Unit): String {
        val resolver = activity.contentResolver
        val failed = { activity.getString(R.string.kairo_create_failed, name) }
        val files = when (blank) {
            is BlankDisk.Image -> listOf(BlankDisk.File("$name.${blank.extension}", blank.size, blank.write))
            is BlankDisk.Folder -> blank.files
            is BlankDisk.Drive -> error("${blank.id} starts a drive, it writes nothing")
        }
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        check(name !in childNames(resolver, tree, root)) { activity.getString(R.string.kairo_create_exists, name, folder) }
        val directory = DocumentsContract.createDocument(resolver, root, Document.MIME_TYPE_DIR, name) ?: error(failed())
        // The folders the files go in, each made once.
        val folders = HashMap<String, Uri>().apply { put("", directory) }
        fun folderOf(path: String): Uri = folders.getOrPut(path) {
            DocumentsContract.createDocument(resolver, folderOf(path.substringBeforeLast('/', "")), Document.MIME_TYPE_DIR,
                path.substringAfterLast('/')) ?: error(failed())
        }
        val tally = Tally(files.map { it.size }, progress)
        for (file in files) {
            val shown = file.path.substringAfterLast('/')
            tally.start(shown, file.size)
            val document = DocumentsContract.createDocument(resolver, folderOf(file.path.substringBeforeLast('/', "")),
                "application/octet-stream", shown) ?: error(failed())
            checkNotNull(resolver.openOutputStream(document, "w")) { failed() }
                .use { output -> file.write(CountingOutput(output, tally::written)) }
        }
        return if (blank is BlankDisk.Image) "$name/${files.single().path}" else name
    }

    /** [output], telling [written] how many bytes have gone through it after each write. */
    private class CountingOutput(private val output: OutputStream, private val written: (Long) -> Unit) : OutputStream() {
        private var count = 0L
        override fun write(b: Int) {
            output.write(b)
            written(++count)
        }
        override fun write(b: ByteArray, off: Int, len: Int) {
            output.write(b, off, len)
            count += len
            written(count)
        }
        override fun flush() = output.flush()
        override fun close() = output.close()
    }

    /** The names in [tree]'s root folder, for a product's own new game ([BlankDisk.Drive]). */
    fun rootNames(resolver: ContentResolver, tree: Uri): Set<String> =
        childNames(resolver, tree, DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)))

    private fun childNames(resolver: ContentResolver, tree: Uri, parent: Uri): Set<String> {
        val query = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(parent))
        return checkNotNull(resolver.query(query, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)) {
            "Could not read the games folder"
        }.use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }
    }
}
