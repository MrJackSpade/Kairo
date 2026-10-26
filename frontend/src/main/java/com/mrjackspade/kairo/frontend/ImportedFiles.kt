package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * Files the player gives the app for games to choose by name, such as a TOS image or a font:
 * each kept once in [directory] as `<hash><extension>`, its ID `<prefix>:<hash>`.
 * [validate] rejects a file before it is kept. [describe] names a kept file and orders it among
 * the others; it gets the name the file had when it was imported, if any.
 */
class ImportedFiles(
    private val directory: File,
    private val prefix: String,
    private val extension: String,
    private val maxBytes: Long,
    private val tooLarge: String,
    private val validate: (File) -> Unit = {},
    private val describe: (file: File, importedName: String?) -> Described,
) {
    class Described(val label: String, val order: Int = 0)
    class Item(val id: String, val file: File, val label: String)

    /** An ID of one of these files, kept or not. */
    val idPattern = Regex(Regex.escape(prefix) + ":[0-9a-f]{16}")

    /** The files as last read; settings pages read them for every row they draw. */
    @Volatile private var cached: List<Item>? = null

    fun items(): List<Item> = cached ?: read().also { cached = it }

    private fun read(): List<Item> = (directory.listFiles { file -> file.name.endsWith(extension) } ?: emptyArray())
        .map { file -> file to describe(file, importedName(file)) }
        .sortedWith(compareBy<Pair<File, Described>> { it.second.order }.thenBy { it.second.label })
        .map { (file, described) -> Item(prefix + ":" + file.name.removeSuffix(extension), file, described.label) }

    private fun nameFile(hash: String) = File(directory, hash + NAME)

    private fun importedName(file: File): String? =
        nameFile(file.name.removeSuffix(extension)).takeIf { it.isFile }?.readText()

    /** The file a game names by [id], or null when it is not kept. */
    fun file(id: String): File? = id.takeIf(idPattern::matches)
        ?.let { File(directory, it.removePrefix("$prefix:") + extension) }?.takeIf { it.isFile }

    /**
     * Keeps the file read from [input], under [name] where the picker gave one, and returns it; a
     * file already kept stays as it is.
     */
    @Synchronized fun add(input: InputStream, name: String? = null): Item {
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create $directory" }
        val partial = File(directory, "import.part")
        val digest = MessageDigest.getInstance("SHA-1")
        try {
            partial.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var size = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    size += count
                    require(size <= maxBytes) { tooLarge }
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                }
                require(size > 0) { "That file is empty" }
            }
            validate(partial)
            val hash = digest.digest().copyOf(8).toHex()
            val file = File(directory, hash + extension)
            if (file.isFile) partial.delete()
            else check(partial.renameTo(file)) { "Could not save $file" }
            if (name != null && !nameFile(hash).isFile) nameFile(hash).writeText(name)
            cached = null
            return items().first { it.id == "$prefix:$hash" }
        } finally {
            partial.delete()
        }
    }

    private companion object {
        /** Beside each file, the name it was imported under. */
        const val NAME = ".name"
    }
}

/**
 * The row under a choice that imports a file into [files] and picks it ([label], such as
 * "Import a TOS image…"), through the system's file chooser; the activity hands the chooser's
 * result to [handle]. [what] names the file in a failure.
 */
class ImportedFilePicker(
    private val activity: Activity,
    private val files: ImportedFiles,
    private val requestCode: Int,
    private val rowId: String,
    private val label: String,
    private val what: String,
    private val toast: (String) -> Unit,
) {
    /** The game (or all games) whose choice an import picks, waiting for the chooser's result. */
    private var picks: ((String) -> Unit)? = null

    fun actions(select: (String) -> Unit): List<SettingsRow> = listOf(
        SettingsRow.action(rowId, label) {
            picks = select
            DocumentPicker.open(activity, requestCode, failed = toast)
        })

    /** True when the result was this picker's. */
    fun handle(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != this.requestCode) return false
        val select = picks ?: return true
        picks = null
        val uri = DocumentPicker.selectedUri(resultCode, data) ?: return true
        Thread {
            val result = runCatching {
                val name = displayName(uri)
                (activity.contentResolver.openInputStream(uri) ?: error("Could not open that file")).use { files.add(it, name) }
            }
            activity.runOnUiThread {
                result.onSuccess { item ->
                    select(item.id)
                    toast("${item.label} imported and chosen")
                }.onFailure { error -> toast("$what import failed: ${error.message ?: error.javaClass.simpleName}") }
            }
        }.apply { name = "kairo-import-$rowId"; start() }
        return true
    }

    private fun displayName(uri: Uri): String? =
        activity.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        }
}
