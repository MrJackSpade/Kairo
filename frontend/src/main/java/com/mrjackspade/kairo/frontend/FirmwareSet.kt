package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The firmware files a machine reads from [directory]. The player picks each file on its own, or
 * a folder from which every file a [Part] recognizes is imported: by a name it accepts, in any
 * case, or by a SHA-1 digest it names, loose or inside a ZIP there (a MAME BIOS set). Every import is checked (size, digest, [Part.validate])
 * before it replaces anything. The machine needs each of [requirements], one of whose parts must
 * be installed; by default each required part is its own requirement.
 */
class FirmwareSet(
    val directory: File,
    val parts: List<Part>,
    val requirements: List<List<Part>> = parts.filter { it.required }.map { listOf(it) },
    /** A short note under the set, such as which games use a machine. */
    val note: UiText? = null,
    /**
     * Whether the Firmware page lists each part as its own row; a set of a whole ROM collection,
     * such as 86Box's thousands of files, shows one row saying how many are installed instead.
     */
    val listed: Boolean = true
) {
    init { require(requirements.all { it.isNotEmpty() && parts.containsAll(it) }) }

    /**
     * One file the machine reads, shown as its own row: [label], with [description] saying in a
     * few words what it is for. [accepted] are the names a folder import finds it by (lower case),
     * [sizes] the sizes a good dump has, or empty for any size. [sha1] are the SHA-1 digests
     * (lower-case hex) of good dumps, which find the file under any name; a part found this way
     * gives its largest size, [maxBytes], so larger files are not read. [validate] checks a copy
     * further and throws to reject it. [status] can name an installed-like state the set does not
     * know (such as a game's own font); [missing] and [removeLabel] name the empty state and the
     * button that returns to it. [identifies] finds the part in a folder import by the first
     * [HEADER_BYTES] of a file of one of its [sizes], for firmware whose dumps differ (versions,
     * languages) but say what they are in a header: it gives a file it recognizes a rank, the
     * highest-ranked file a folder holds is imported, and null for any other file. It only sorts
     * what a folder holds, and never refuses a file the player picks.
     */
    class Part(val name: String, val label: UiText, val description: UiText, val accepted: Set<String>,
               val required: Boolean, val sizes: Set<Long> = emptySet(), val sha1: Set<String> = emptySet(),
               val maxBytes: Long = sizes.maxOrNull() ?: Long.MAX_VALUE, val validate: (File) -> Unit = {},
               val status: (() -> UiText?)? = null, val missing: UiText = UiText.res(R.string.kairo_missing),
               val removeLabel: UiText = UiText.res(R.string.kairo_remove), val identifies: ((ByteArray) -> Int?)? = null) {
        init {
            require(description !is UiText.Fixed || description.text.isNotBlank()) { "$name has no description" }
            require(sha1.isEmpty() || maxBytes < Long.MAX_VALUE) { "$name is found by content but has no size limit" }
            require(identifies == null || sizes.isNotEmpty()) { "$name is found by its header but has no sizes" }
        }
    }

    private val hashed = parts.filter { it.sha1.isNotEmpty() }
    /** The hashed parts by digest: one file can be several parts, as one ROM read at two places. */
    private val byDigest: Map<String, List<Part>> = hashed.flatMap { part -> part.sha1.map { it to part } }
        .groupBy({ it.first }, { it.second })
    private val largestHashed = hashed.maxOfOrNull { it.maxBytes } ?: 0L
    private val headed = parts.filter { it.identifies != null }
    /** Each accepted name's part, the first part that accepts it: a folder import looks names up here, not part by part. */
    private val byAcceptedName: Map<String, Part> = buildMap { for (part in parts) for (name in part.accepted) putIfAbsent(name, part) }

    fun file(part: Part) = File(directory, part.name)
    fun installed(part: Part) = file(part).isFile
    /** The requirements none of whose parts is installed. */
    val missingRequirements: List<List<Part>> get() = requirements.filter { it.none(::installed) }
    /** [missingRequirements] for a message, such as "IPL-ROM, CG-ROM or CG-ROM (XVI)". */
    fun missingText(context: Context): String = missingRequirements.joinToString(context.getString(R.string.kairo_list_separator)) { group ->
        group.joinToString(context.getString(R.string.kairo_list_or)) { it.label.resolve(context) }
    }
    val installedCount: Int get() = parts.count(::installed)

    /**
     * Whether the machine needs [part]. A part that stands in for several others, such as one dump
     * of a whole EPROM in place of the separate ROMs it holds, is optional; so is a part no
     * requirement names.
     */
    fun isRequired(part: Part): Boolean {
        val groups = requirements.filter { part in it }
        if (groups.isEmpty()) return false
        val standIn = groups.count { it.size > 1 } > 1
        return !standIn
    }

    /** The row's state: what is installed, or what covers a missing part. */
    fun state(part: Part): UiText {
        part.status?.invoke()?.let { return it }
        if (installed(part)) return UiText.res(R.string.kairo_installed)
        // Not installed, but every requirement it is part of is met by another file.
        val groups = requirements.filter { part in it }
        val cover = groups.flatMap { group -> group.filter { it !== part && installed(it) } }.firstOrNull()
        return if (cover != null && groups.all { group -> group.any(::installed) }) UiText.res(R.string.kairo_firmware_covered_by, cover.label)
            else part.missing
    }

    /**
     * Opens the system folder picker at [start] (FolderStart), not wherever it was last; the result
     * goes to [importFolder].
     */
    fun chooseFolder(activity: Activity, requestCode: Int, start: Uri?, failed: (String) -> Unit) = try {
        @Suppress("DEPRECATION")
        activity.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .apply { if (start != null) putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, start) }, requestCode)
    } catch (error: android.content.ActivityNotFoundException) {
        failed(error.message ?: activity.getString(R.string.kairo_folder_chooser_failed))
    }

    /**
     * Copies every recognized file under [tree], replacing earlier copies, and returns the parts it
     * imported. Runs on a worker thread. A file that fails its part's checks fails the import
     * before anything is replaced.
     */
    fun importFolder(context: Context, tree: Uri, cancelled: AtomicBoolean): List<Part> {
        val files = DocumentTreeWalker(context).scan(tree, cancelled,
            { name -> hashed.isNotEmpty() || headed.isNotEmpty() || partFor(name) != null || isZip(name) }, {}).files
        val found = choose(candidates(context, files, cancelled), cancelled)
        // Every copy is checked before any replaces an installed file.
        val staged = found.map { (part, file) -> part to stage(context, part, file.open) }
        try {
            for ((part, copy) in staged) commit(copy, part)
        } finally {
            staged.forEach { it.second.delete() }
        }
        return found.map { it.first }
    }

    /**
     * The file each part takes from a folder's [candidates]: by a name it accepts, then by its
     * digest (one file can be several parts), then by its header.
     */
    internal fun choose(candidates: List<Candidate>, cancelled: AtomicBoolean): List<Pair<Part, Candidate>> {
        // Each part's first candidate by name, in one pass over the candidates (a ROM set has thousands of both).
        val firstByName = HashMap<Part, Candidate>()
        for (candidate in candidates) partFor(candidate.name)?.let { firstByName.putIfAbsent(it, candidate) }
        val byName = parts.mapNotNull { part -> firstByName[part]?.let { part to it } }
        // Then by content: each file no larger than a hashed part can be, read once.
        val byContent = candidates.filter { it.size in 1..largestHashed }.flatMap { file ->
            if (cancelled.get()) throw java.util.concurrent.CancellationException("Cancelled")
            val digest = file.open().use(::sha1)
            byDigest[digest].orEmpty().map { it to file }
        }
        // Then by header: each file of a size a headed part has, its first bytes read once; a part
        // takes the file it ranks highest.
        val byHeader = candidates.filter { file -> headed.any { file.size in it.sizes } }.flatMap { file ->
            if (cancelled.get()) throw java.util.concurrent.CancellationException("Cancelled")
            val header = file.open().use(::header)
            headed.mapNotNull { part ->
                if (file.size !in part.sizes) null
                else part.identifies!!(header)?.let { rank -> Triple(part, rank, file) }
            }
        }.groupBy { it.first }.map { (part, matches) -> part to matches.maxBy { it.second }.third }
        return (byName + byContent + byHeader).distinctBy { it.first }
    }

    /** A file a folder import can take: loose in the folder, or a member of a ZIP there. */
    internal class Candidate(val name: String, val size: Long, val open: () -> java.io.InputStream)

    private fun isZip(name: String) = name.lowercase(Locale.ROOT).endsWith(".zip")

    /** The folder's loose files, then the members of each ZIP in it, by their own names. */
    private fun candidates(context: Context, files: List<DocumentTreeWalker.FileEntry>,
                           cancelled: AtomicBoolean): List<Candidate> {
        val resolver = context.contentResolver
        fun open(uri: Uri) = resolver.openInputStream(uri) ?: error("Unable to open $uri")
        val loose = files.filter { !isZip(it.path) }.map { file ->
            Candidate(file.path.substringAfterLast('/'), file.size) { open(file.uri) }
        }
        val members = files.filter { isZip(it.path) }.flatMap { zip ->
            if (cancelled.get()) throw java.util.concurrent.CancellationException("Cancelled")
            readZip(resolver, zip.uri) { archive ->
                archive.entries.asSequence().filter { !it.isDirectory }.map { entry ->
                    Candidate(entry.name.substringAfterLast('/'), entry.size) {
                        // The member's bytes, read through its own open of the ZIP, closed with the stream.
                        val document = resolver.openFileDescriptor(zip.uri, "r") ?: error("Unable to open ${zip.path}")
                        val reopened = org.apache.commons.compress.archivers.zip.ZipFile.builder()
                            .setSeekableByteChannel(java.io.FileInputStream(document.fileDescriptor).channel).get()
                        val input = reopened.getInputStream(reopened.getEntry(entry.name))
                        object : java.io.FilterInputStream(input) {
                            override fun close() {
                                try { super.close() } finally { reopened.close(); document.close() }
                            }
                        }
                    }
                }.toList()
            }
        }
        return loose + members
    }

    private fun <T> readZip(resolver: ContentResolver, uri: Uri,
                            read: (org.apache.commons.compress.archivers.zip.ZipFile) -> T): T =
        (resolver.openFileDescriptor(uri, "r") ?: error("Unable to open $uri")).use { document ->
            org.apache.commons.compress.archivers.zip.ZipFile.builder()
                .setSeekableByteChannel(java.io.FileInputStream(document.fileDescriptor).channel).get().use(read)
        }

    /** Copies the file the player picked for [part], replacing its installed copy once it passes. */
    fun importPart(context: Context, uri: Uri, part: Part) {
        val copy = stage(context, part) { context.contentResolver.openInputStream(uri) ?: error("Unable to open the selected file") }
        try { commit(copy, part) } finally { copy.delete() }
    }

    fun remove(context: Context, part: Part) = check(!file(part).exists() || file(part).delete()) {
        context.getString(R.string.kairo_firmware_remove_failed, part.label.resolve(context))
    }

    /** A checked copy of what [open] reads for [part], beside its installed file. */
    private fun stage(context: Context, part: Part, open: () -> java.io.InputStream): File {
        // A part's name can be a path in the set, such as machines/486ap4/bios.bin.
        val folder = checkNotNull(file(part).parentFile) { "${part.name} has no folder" }
        check(folder.isDirectory || folder.mkdirs()) { "Cannot create the firmware folder ${folder.name}" }
        val copy = File(folder, "${file(part).name}.part")
        val label = part.label.resolve(context)
        try {
            importStream(context, open, copy, label, maxBytes = part.maxBytes) { file ->
                require(part.sizes.isEmpty() || file.length() in part.sizes) {
                    context.getString(R.string.kairo_import_wrong_size, label, bytes(context, file.length()),
                        part.sizes.joinToString(context.getString(R.string.kairo_list_or)) { describe(context, it) })
                }
                require(part.sha1.isEmpty() || sha1(file) in part.sha1) {
                    context.getString(R.string.kairo_firmware_unknown_dump, label)
                }
                part.validate(file)
            }
        } catch (error: Exception) {
            copy.delete()
            throw error
        }
        return copy
    }

    private fun commit(copy: File, part: Part) {
        java.nio.file.Files.move(copy.toPath(), file(part).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }

    private fun partFor(name: String): Part? = byAcceptedName[name.lowercase(Locale.ROOT)]

    /** The first [HEADER_BYTES] of [input], fewer when it is shorter. */
    private fun header(input: java.io.InputStream): ByteArray {
        val bytes = ByteArray(HEADER_BYTES)
        var count = 0
        while (count < bytes.size) {
            val read = input.read(bytes, count, bytes.size - count)
            if (read < 0) break
            count += read
        }
        return bytes.copyOf(count)
    }

    private fun sha1(file: File): String = file.inputStream().use(::sha1)

    private fun sha1(input: java.io.InputStream): String {
        val digest = java.security.MessageDigest.getInstance("SHA-1")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        return digest.digest().toHex()
    }

    companion object {
        /** How much of a file [Part.identifies] reads. */
        const val HEADER_BYTES = 256

        /**
         * Copies one picked file to [target], replacing it only once the copy is whole and good: it
         * must be exactly [exactBytes] when given, at most [maxBytes], not empty, and pass [validate].
         * Runs on a worker thread; [label] names the file in errors.
         */
        fun importFile(context: Context, uri: Uri, target: File, label: String, exactBytes: Long? = null,
                       maxBytes: Long = exactBytes ?: Long.MAX_VALUE, validate: (File) -> Unit = {}) =
            importStream(context, { context.contentResolver.openInputStream(uri) ?: error("Unable to open the selected file") },
                target, label, exactBytes, maxBytes, validate)

        /** [importFile] of what [open] reads. */
        fun importStream(context: Context, open: () -> java.io.InputStream, target: File, label: String,
                         exactBytes: Long? = null, maxBytes: Long = exactBytes ?: Long.MAX_VALUE,
                         validate: (File) -> Unit = {}) {
            val directory = checkNotNull(target.parentFile) { "$label has no folder" }
            check(directory.isDirectory || directory.mkdirs()) { "Cannot create ${directory.name}" }
            val partial = File(directory, "${target.name}.part")
            val sizeRule = exactBytes?.let { context.getString(R.string.kairo_import_exact_size, label, describe(context, it)) }
                ?: context.getString(R.string.kairo_import_too_large, label)
            try {
                open().use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var size = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            size += count
                            require(size <= maxBytes) { sizeRule }
                            output.write(buffer, 0, count)
                        }
                        require(size > 0) { context.getString(R.string.kairo_import_empty, label) }
                        require(exactBytes == null || size == exactBytes) { sizeRule }
                    }
                }
                validate(partial)
                java.nio.file.Files.move(partial.toPath(), target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            } finally {
                partial.delete()
            }
        }

        private fun describe(context: Context, bytes: Long): String =
            if (bytes % 1024 == 0L) context.getString(R.string.kairo_size_kib, bytes / 1024)
            else bytes(context, bytes)

        private fun bytes(context: Context, count: Long): String = context.resources.getQuantityString(
            R.plurals.kairo_size_bytes, count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), count)
    }
}
