package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The firmware files a machine reads from [directory]. The player picks each file on its own, or
 * a folder from which every file a [Part] recognizes is imported: by a name it accepts, in any
 * case, or by a SHA-1 digest it names. Every import is checked (size, digest, [Part.validate])
 * before it replaces anything. The machine needs each of [requirements], one of whose parts must
 * be installed; by default each required part is its own requirement.
 */
class FirmwareSet(
    val directory: File,
    val parts: List<Part>,
    val requirements: List<List<Part>> = parts.filter { it.required }.map { listOf(it) },
    /** A short note under the set, such as which games use a machine. */
    val note: String? = null
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
     * button that returns to it.
     */
    class Part(val name: String, val label: String, val description: String, val accepted: Set<String>,
               val required: Boolean, val sizes: Set<Long> = emptySet(), val sha1: Set<String> = emptySet(),
               val maxBytes: Long = sizes.maxOrNull() ?: Long.MAX_VALUE, val validate: (File) -> Unit = {},
               val status: (() -> String?)? = null, val missing: String = "Missing",
               val removeLabel: String = "Remove") {
        init {
            require(description.isNotBlank()) { "$label has no description" }
            require(sha1.isEmpty() || maxBytes < Long.MAX_VALUE) { "$label is found by content but has no size limit" }
        }
    }

    private val hashed = parts.filter { it.sha1.isNotEmpty() }
    private val largestHashed = hashed.maxOfOrNull { it.maxBytes } ?: 0L

    fun file(part: Part) = File(directory, part.name)
    fun installed(part: Part) = file(part).isFile
    /** The requirements none of whose parts is installed. */
    val missingRequirements: List<List<Part>> get() = requirements.filter { it.none(::installed) }
    /** [missingRequirements] for a message, such as "IPL-ROM, CG-ROM or CG-ROM (XVI)". */
    val missingText: String get() = missingRequirements.joinToString(", ") { group -> group.joinToString(" or ") { it.label } }
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
    fun state(part: Part): String {
        part.status?.invoke()?.let { return it }
        if (installed(part)) return "Installed"
        // Not installed, but every requirement it is part of is met by another file.
        val groups = requirements.filter { part in it }
        val cover = groups.flatMap { group -> group.filter { it !== part && installed(it) } }.firstOrNull()
        return if (cover != null && groups.all { group -> group.any(::installed) }) "Covered by ${cover.label}"
            else part.missing
    }

    /** Opens the system folder picker; the result goes to [importFolder]. */
    fun chooseFolder(activity: Activity, requestCode: Int, failed: (String) -> Unit) = try {
        @Suppress("DEPRECATION")
        activity.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), requestCode)
    } catch (error: android.content.ActivityNotFoundException) {
        failed(error.message ?: "Could not open the folder chooser")
    }

    /**
     * Copies every recognized file under [tree], replacing earlier copies, and returns the parts it
     * imported. Runs on a worker thread. A file that fails its part's checks fails the import
     * before anything is replaced.
     */
    fun importFolder(resolver: ContentResolver, tree: Uri, cancelled: AtomicBoolean): List<Part> {
        val files = DocumentTreeWalker(resolver).scan(tree, cancelled,
            { name -> hashed.isNotEmpty() || partFor(name) != null }, {}).files
        val byName = parts.mapNotNull { part ->
            files.firstOrNull { partFor(it.path.substringAfterLast('/')) === part }?.let { part to it.uri }
        }
        // Then by content: each file no larger than a hashed part can be, read once.
        val byContent = files.filter { it.size in 1..largestHashed }.mapNotNull { file ->
            if (cancelled.get()) throw java.util.concurrent.CancellationException("Cancelled")
            val digest = sha1(resolver, file.uri)
            hashed.firstOrNull { digest in it.sha1 }?.let { it to file.uri }
        }
        val found = byName + byContent.distinctBy { it.first }.filter { (part, _) -> byName.none { it.first === part } }
        // Every copy is checked before any replaces an installed file.
        val staged = found.map { (part, uri) -> part to stage(resolver, uri, part) }
        try {
            for ((part, copy) in staged) commit(copy, part)
        } finally {
            staged.forEach { it.second.delete() }
        }
        return found.map { it.first }
    }

    /** Copies the file the player picked for [part], replacing its installed copy once it passes. */
    fun importPart(resolver: ContentResolver, uri: Uri, part: Part) {
        val copy = stage(resolver, uri, part)
        try { commit(copy, part) } finally { copy.delete() }
    }

    fun remove(part: Part) = check(!file(part).exists() || file(part).delete()) { "Could not remove ${part.label}" }

    /** A checked copy of [uri] for [part], beside its installed file. */
    private fun stage(resolver: ContentResolver, uri: Uri, part: Part): File {
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create the firmware folder" }
        val copy = File(directory, "${part.name}.part")
        try {
            importFile(resolver, uri, copy, part.label, maxBytes = part.maxBytes) { file ->
                require(part.sizes.isEmpty() || file.length() in part.sizes) {
                    "${part.label} is ${file.length()} bytes; it must be ${part.sizes.joinToString(" or ") { describe(it) }}"
                }
                require(part.sha1.isEmpty() || sha1(file) in part.sha1) { "That file is not a known ${part.label} dump" }
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

    private fun partFor(name: String): Part? {
        val lower = name.lowercase(Locale.ROOT)
        return parts.firstOrNull { lower in it.accepted }
    }

    private fun sha1(resolver: ContentResolver, uri: Uri): String =
        (resolver.openInputStream(uri) ?: error("Unable to open $uri")).use(::sha1)

    private fun sha1(file: File): String = file.inputStream().use(::sha1)

    private fun sha1(input: java.io.InputStream): String {
        val digest = java.security.MessageDigest.getInstance("SHA-1")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        /**
         * Copies one picked file to [target], replacing it only once the copy is whole and good: it
         * must be exactly [exactBytes] when given, at most [maxBytes], not empty, and pass [validate].
         * Runs on a worker thread; [label] names the file in errors.
         */
        fun importFile(resolver: ContentResolver, uri: Uri, target: File, label: String, exactBytes: Long? = null,
                       maxBytes: Long = exactBytes ?: Long.MAX_VALUE, validate: (File) -> Unit = {}) {
            val directory = checkNotNull(target.parentFile) { "$label has no folder" }
            check(directory.isDirectory || directory.mkdirs()) { "Cannot create ${directory.name}" }
            val partial = File(directory, "${target.name}.part")
            val sizeRule = exactBytes?.let { "$label must be exactly ${describe(it)}" } ?: "$label is too large"
            try {
                (resolver.openInputStream(uri) ?: error("Unable to open the selected file")).use { input ->
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
                        require(size > 0) { "$label is empty" }
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

        private fun describe(bytes: Long) = if (bytes % 1024 == 0L) "${bytes / 1024} KiB" else "$bytes bytes"
    }
}
