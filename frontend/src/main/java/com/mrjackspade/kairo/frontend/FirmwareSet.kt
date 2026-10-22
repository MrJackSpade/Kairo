package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The firmware files a machine reads from [directory], imported from a folder the player chooses:
 * every file there whose name a [Part] accepts, in any case, or whose content a part names by
 * SHA-1, is copied under the part's name.
 */
class FirmwareSet(val directory: File, val parts: List<Part>) {
    /**
     * One file the machine reads. [accepted] are the names it is found by (lower case), [sizes]
     * the sizes a good dump has, or empty for any size. [sha1] are the SHA-1 digests (lower-case
     * hex) of good dumps, which find the file under any name; a part found this way gives its
     * largest size, [maxBytes], so larger files are not read.
     */
    class Part(val name: String, val label: String, val accepted: Set<String>, val required: Boolean,
               val sizes: Set<Long> = emptySet(), val sha1: Set<String> = emptySet(),
               val maxBytes: Long = sizes.maxOrNull() ?: Long.MAX_VALUE) {
        init { require(sha1.isEmpty() || maxBytes < Long.MAX_VALUE) { "$label is found by content but has no size limit" } }
    }

    private val hashed = parts.filter { it.sha1.isNotEmpty() }
    private val largestHashed = hashed.maxOfOrNull { it.maxBytes } ?: 0L

    fun file(part: Part) = File(directory, part.name)
    fun installed(part: Part) = file(part).isFile
    val missingRequired: List<Part> get() = parts.filter { it.required && !installed(it) }
    val installedCount: Int get() = parts.count(::installed)

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
     * imported. Runs on a worker thread. A file of the wrong size fails the import before anything
     * is replaced.
     */
    fun importFolder(resolver: ContentResolver, tree: Uri, cancelled: AtomicBoolean): List<Part> {
        val files = DocumentTreeWalker(resolver).scan(tree, cancelled,
            { name -> hashed.isNotEmpty() || partFor(name) != null }, {}).files
        val byName = parts.mapNotNull { part ->
            files.firstOrNull { partFor(it.path.substringAfterLast('/')) === part }?.let { part to it }
        }
        // Then by content: each file no larger than a hashed part can be, read once.
        val byContent = files.filter { it.size in 1..largestHashed }.mapNotNull { file ->
            if (cancelled.get()) throw java.util.concurrent.CancellationException("Cancelled")
            val digest = sha1(resolver, file.uri)
            hashed.firstOrNull { digest in it.sha1 }?.let { it to file }
        }
        val found = byName + byContent.distinctBy { it.first }.filter { (part, _) -> byName.none { it.first === part } }
        for ((part, file) in found) require(part.sizes.isEmpty() || file.size in part.sizes) {
            "${file.path.substringAfterLast('/')} is ${file.size} bytes; ${part.label} is " +
                part.sizes.joinToString(" or ") { "$it bytes" }
        }
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create the firmware folder" }
        for ((part, file) in found) {
            if (cancelled.get()) throw java.util.concurrent.CancellationException("Cancelled")
            val partial = File(directory, "${part.name}.part")
            try {
                resolver.openInputStream(file.uri)?.use { input -> partial.outputStream().use(input::copyTo) }
                    ?: error("Unable to open ${file.path}")
                check(partial.renameTo(file(part))) { "Could not save ${part.label}" }
            } finally {
                partial.delete()
            }
        }
        return found.map { it.first }
    }

    /**
     * The set as one row: Installed, or what is missing, with a tray to choose the folder.
     * [label] names the set, such as "PC-88 ROMs".
     */
    fun row(id: String, label: String, busy: () -> Boolean, enabled: () -> Boolean, disabledReason: () -> String,
            choose: () -> Unit) = SettingsRow.action(id, label,
        value = {
            val missing = missingRequired
            when {
                busy() -> "Importing…"
                installedCount == 0 -> "Not installed"
                missing.isEmpty() -> "Installed"
                else -> "Missing ${missing.joinToString(", ") { it.label }}"
            }
        },
        tone = { if (missingRequired.isEmpty() && installedCount > 0) ValueTone.CURRENT else ValueTone.SUMMARY },
        busy = busy, enabled = { !busy() && enabled() },
        disabledReason = { if (busy()) "Wait for the current import to finish" else disabledReason() },
        tray = { TraySpec(buttons = listOf(TrayButton(if (installedCount > 0) "Replace…" else "Choose folder…",
            ButtonTone.PRIMARY) { choose() })) })

    fun remove(part: Part) = check(!file(part).exists() || file(part).delete()) { "Could not remove ${part.label}" }

    private fun partFor(name: String): Part? {
        val lower = name.lowercase(Locale.ROOT)
        return parts.firstOrNull { lower in it.accepted }
    }

    private fun sha1(resolver: ContentResolver, uri: Uri): String {
        val digest = java.security.MessageDigest.getInstance("SHA-1")
        (resolver.openInputStream(uri) ?: error("Unable to open $uri")).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
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
