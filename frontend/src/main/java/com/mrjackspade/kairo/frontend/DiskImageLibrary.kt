package com.mrjackspade.kairo.frontend

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.CRC32
import java.util.zip.ZipFile

/** A disk image: loose in the ROM folder, or a member of a ZIP or 7z archive there. */
data class DiskImageEntry(
    override val id: String,
    override val uri: String,
    override val path: String,
    /** The image's path inside its archive, or null for a loose image. */
    override val zipEntry: String?,
    val sourceSize: Long,
    val sourceModified: Long,
    val imageSize: Long,
    val imageCrc: Long,
    override val contentId: String?,
    override val error: String? = null,
    override val catalogId: String? = contentId,
    /** What the library calls the image, from its [DiskImageLibrary.Format]. */
    override val mediaLabel: String = "Disk image"
) : LibraryItem {
    override val displayName: String get() = (zipEntry ?: path).substringAfterLast('/').substringAfterLast('\\')
    override val playable: Boolean get() = contentId != null && error == null
}

/** Entries from the same archive, or from the same folder for a loose image. */
fun sameSource(entry: DiskImageEntry, other: DiskImageEntry): Boolean =
    if (entry.zipEntry != null) other.zipEntry != null && other.uri == entry.uri
    else other.zipEntry == null &&
        other.path.substringBeforeLast('/', "") == entry.path.substringBeforeLast('/', "")

/**
 * A product's disk images in its ROM folder: loose, or inside ZIP and 7z archives. Each image is
 * named by the SHA-256 of its bytes (`sha256-<kind>-v1:<hex>`, the catalog's content IDs), and a
 * game plays from an app-private working copy that keeps what it writes to its disks.
 */
class DiskImageLibrary(private val context: Context, private val format: Format) : MachineLibrary<DiskImageEntry> {
    /** The images a product reads, by file extension. */
    class Format(
        /** Image extensions (lower case) and the content ID kind each is hashed as, such as "fd". */
        val kinds: Map<String, String>,
        /** The library's label for each kind, such as "Floppy disk". */
        val labels: Map<String, String>,
        /** The largest image of each kind. */
        val maxBytes: Map<String, Long>,
        /** The machine's name, for messages such as "No ${format.guestName} disk image in archive". */
        val guestName: String
    ) {
        init { require(kinds.values.all { it in labels && it in maxBytes }) }
        fun extension(name: String): String = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        fun kind(name: String): String? = kinds[extension(name)]
        fun isImage(name: String) = kind(name) != null
        fun isArchive(name: String) = extension(name) in ARCHIVES
        private val idPattern = Regex("sha256-(?:${kinds.values.distinct().joinToString("|")})-v1:[0-9a-f]{64}")
        fun validId(value: String) = idPattern.matches(value)
        fun limit(name: String): Long = maxBytes.getValue(kind(name) ?: error("$name is not a $guestName disk image"))

        companion object { private val ARCHIVES = setOf("zip", "7z") }
    }

    private val storeCache = VersionedLibraryCache(File(context.filesDir, "library-v1.bin"), 1,
        MAX_STORE_BYTES, MAX_DOCUMENTS, ::writeCacheRecord, ::readCacheRecord)
    private val scanPipeline by lazy {
        LibraryScanPipeline(storeCache, DiskImageEntry::id, ::planScan, ::inspectSource, ::failedSource,
            { source, index, total -> "Scanning ${index + 1}/$total: ${source.path}" },
            displayOrder = { entries -> entries.sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER) { it.path + (it.zipEntry ?: "") }) },
            metadata = { JSONObject().put("mediaFormatsVersion", MEDIA_FORMATS_VERSION) },
            afterCommit = { plan -> pruneArchives(plan.work) })
    }
    private val archiveCache = File(context.cacheDir, "rom-archives").apply { mkdirs() }
    private val imageStore = File(context.filesDir, "media").apply {
        mkdirs()
        listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() }
    }

    override val hashCount: Int get() = scanPipeline.hashCount

    override fun cached(tree: Uri): List<DiskImageEntry> = scanPipeline.cached(tree)

    override fun store(tree: Uri, entries: List<DiskImageEntry>) = scanPipeline.store(tree, entries)

    override fun scan(tree: Uri, forceHash: Boolean, cancelled: AtomicBoolean,
                      progress: (String) -> Unit): List<DiskImageEntry> = scanPipeline.scan(tree, forceHash, cancelled, progress)

    /** Delete the source document shared by every image entry from that file. */
    override fun deleteSource(entry: DiskImageEntry, entries: List<DiskImageEntry>) {
        val sourceUri = Uri.parse(entry.uri)
        require(DocumentsContract.isDocumentUri(context, sourceUri)) { "This game is not a removable library document" }
        require(DocumentsContract.deleteDocument(context.contentResolver, sourceUri)) {
            "The document provider could not delete the game file"
        }
        val archiveKey = sha256(entry.uri.toByteArray()).take(32)
        archiveCache.listFiles()?.filter { it.name.startsWith("$archiveKey-") || it.name == "$archiveKey.part" }
            ?.forEach { it.delete() }
        entries.filter { it.uri == entry.uri }.forEach(::resetWorkingImage)
    }

    /** The working copy the game plays from, with the saves written into it; the next launch copies the source again. */
    override fun resetWorkingImage(entry: DiskImageEntry) {
        val file = workingImage(entry)
        check(!file.exists() || file.delete()) { "Could not remove the working copy of ${entry.displayName}" }
    }

    override fun hasWorkingImage(entry: DiskImageEntry) = workingImage(entry).isFile

    /** The working copy of [entry], whether or not it exists yet. */
    fun workingImage(entry: DiskImageEntry) =
        File(imageStore, "${entry.key.storageName}.${format.extension(entry.displayName)}")

    /** The library entry whose working copy is [file], for a disk the machine holds. */
    fun entryForWorkingImage(file: String, entries: List<DiskImageEntry>): DiskImageEntry? =
        entries.firstOrNull { workingImage(it).absolutePath == file }

    /** Inspect one launcher-provided document without requiring a selected ROM tree. */
    override fun inspectExternal(file: ExternalGameFile, cancelled: AtomicBoolean): List<DiskImageEntry> {
        val source = Source(file.uri, file.name, file.size, file.modified)
        if (format.isImage(file.name)) {
            val candidate = entry(source, null, file.size, -1, null, key = GameKey(file.uri.toString()))
            val id = context.contentResolver.openInputStream(file.uri)?.use {
                contentId(it, file.size, -1, cancelled, file.name)
            } ?: error("Unable to open ${file.name}")
            return listOf(candidate.copy(contentId = id, catalogId = id))
        }
        require(format.isArchive(file.name)) { "Unsupported ${format.guestName} game file: ${file.name}" }
        return openArchive(cachedArchive(source, false, cancelled)).use { archive ->
            archive.images { checkCancelled(cancelled) }.map { member ->
                val id = archive.open(member).use { contentId(it, member.size, member.crc, cancelled, member.name) }
                entry(source, member.name, member.size, member.crc, id,
                    key = GameKey.inArchive(file.uri.toString(), member.name))
            }
        }
    }

    private fun planScan(tree: Uri, cancelled: AtomicBoolean,
                         progress: (String) -> Unit): LibraryScanPipeline.Plan<Source, DiskImageEntry> {
        val result = DocumentTreeWalker(context.contentResolver).scan(tree, cancelled,
            { name -> format.isImage(name) || format.isArchive(name) }, progress)
        return LibraryScanPipeline.Plan(result.files.map { Source(it.uri, it.path, it.size, it.modified) },
            initialEntries = result.folders.map { folder ->
                entry(Source(folder.uri, folder.path, -1, 0), null, -1, -1, null, "Unreadable folder: ${folder.message}")
            })
    }

    /** The content each key was first scanned with stays its catalog record through later edits. */
    private fun inspectSource(source: Source, scan: LibraryScanPipeline.ScanContext<DiskImageEntry>): List<DiskImageEntry> =
        inspectContent(source, scan).map { it.copy(catalogId = scan.earlier[it.id]?.catalogId ?: it.contentId) }

    private fun inspectContent(source: Source, scan: LibraryScanPipeline.ScanContext<DiskImageEntry>): List<DiskImageEntry> {
        if (format.isArchive(source.path)) {
            val oldEntries = scan.prior.values.filter { it.uri == source.uri.toString() }
            val formatsCurrent = (scan.previous?.root?.optInt("mediaFormatsVersion", 0) ?: 0) >= MEDIA_FORMATS_VERSION
            if (!scan.forceHash && formatsCurrent && trusted(source) && oldEntries.isNotEmpty() &&
                oldEntries.all { sameSource(it, source) && it.error == null }) return oldEntries
            return openArchive(cachedArchive(source, scan.forceHash, scan.cancelled)).use { archive ->
                archive.images(scan::checkCancelled).map { member ->
                    scan.checkCancelled()
                    val candidate = entry(source, member.name, member.size, member.crc, null)
                    val old = scan.prior[candidate.id]
                    try {
                        val id = if (!scan.forceHash && trusted(source) && old != null &&
                            sameFingerprint(old, candidate) && old.contentId?.let(format::validId) == true) old.contentId
                        else {
                            scan.progress("Hashing ${member.name}")
                            scan.hashStarted()
                            archive.open(member).use { contentId(it, member.size, member.crc, scan.cancelled, member.name) }
                        }
                        candidate.copy(contentId = id)
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (error: Exception) {
                        scan.checkCancelled()
                        candidate.copy(error = error.message ?: "Disk image unreadable")
                    }
                }
            }
        }
        val candidate = entry(source, null, source.size, -1, null)
        val old = scan.prior[candidate.id]
        val id = if (!scan.forceHash && trusted(source) && old != null &&
            sameFingerprint(old, candidate) && old.contentId?.let(format::validId) == true) old.contentId
        else {
            scan.progress("Hashing ${source.path}")
            scan.hashStarted()
            context.contentResolver.openInputStream(source.uri)?.use {
                contentId(it, source.size, -1, scan.cancelled, source.path)
            } ?: error("Unable to open disk image")
        }
        return listOf(candidate.copy(contentId = id))
    }

    private fun failedSource(source: Source, scan: LibraryScanPipeline.ScanContext<DiskImageEntry>,
                             error: Exception): List<DiskImageEntry> = listOf(
        entry(source, null, source.size, -1, null,
            error.message ?: if (format.isArchive(source.path)) "Archive unreadable" else "Disk image unreadable",
            catalogId = scan.earlier[source.path]?.catalogId))

    /**
     * Returns the app-private working image for this entry, copying it from its source the first
     * time. The copy is verified against the entry's content ID.
     */
    @Synchronized fun prepare(entry: DiskImageEntry, cancelled: AtomicBoolean, progress: (String) -> Unit): File {
        require(entry.playable) { "This entry is not ready to launch" }
        // The working copy holds the game's in-disk saves, so it stays when the source is edited.
        val file = workingImage(entry)
        if (file.isFile && file.length() > 0) return file
        if (entry.imageSize > 0) require(imageStore.usableSpace > entry.imageSize + 16L * 1024 * 1024) {
            "Not enough free space for this disk image"
        }
        val partial = File(imageStore, "${file.name}.part")
        partial.delete()
        try {
            progress("Preparing ${entry.displayName}")
            if (entry.zipEntry == null) {
                context.contentResolver.openInputStream(Uri.parse(entry.uri))?.use { input ->
                    partial.outputStream().use { output -> copyVerified(entry, input, -1, cancelled, output) }
                } ?: error("Unable to open disk image")
            } else {
                openArchive(archive(entry, cancelled)).use { archive ->
                    val member = archive.images { checkCancelled(cancelled) }.firstOrNull { it.name == entry.zipEntry }
                        ?: error("${entry.zipEntry} is missing from the archive")
                    archive.open(member).use { input ->
                        partial.outputStream().use { output -> copyVerified(entry, input, member.crc, cancelled, output) }
                    }
                }
            }
            require(partial.renameTo(file)) { "Unable to save working disk image" }
            return file
        } catch (error: Exception) {
            partial.delete()
            throw error
        }
    }

    private fun copyVerified(entry: DiskImageEntry, input: InputStream, crc: Long, cancelled: AtomicBoolean,
                             output: OutputStream) {
        require(contentId(input, entry.imageSize, crc, cancelled, entry.displayName, output) == entry.contentId) {
            "Disk image changed since scan; refresh the library"
        }
    }

    /** The local copy of the archive that holds [entry]. */
    private fun archive(entry: DiskImageEntry, cancelled: AtomicBoolean): File {
        require(entry.zipEntry != null) { "${entry.displayName} is not in an archive" }
        return cachedArchive(Source(Uri.parse(entry.uri), entry.path, entry.sourceSize, entry.sourceModified),
            false, cancelled)
    }

    private data class Source(val uri: Uri, val path: String, val size: Long, val modified: Long)

    /** An image in an archive: its normalized path, size, and CRC-32 when the archive records one. */
    private class Member(val name: String, val size: Long, val crc: Long)

    /** A ZIP or 7z archive's floppy images. */
    private interface Archive : Closeable {
        fun images(checkCancelled: () -> Unit): List<Member>
        fun open(member: Member): InputStream
    }

    private fun openArchive(file: File): Archive =
        if (file.name.endsWith(".7z")) SevenZArchive(file) else ZipArchive(file)

    private inner class ZipArchive(file: File) : Archive {
        private val zip = ZipFile(file)
        private val byName = HashMap<String, java.util.zip.ZipEntry>()

        override fun images(checkCancelled: () -> Unit): List<Member> {
            val names = HashSet<String>()
            val members = ArrayList<Member>()
            var inspected = 0
            for (item in zip.entries()) {
                checkCancelled()
                require(++inspected <= MAX_ARCHIVE_ENTRIES) { "Archive has too many entries" }
                val name = safeEntryName(item.name)
                require(names.add(name.lowercase(Locale.ROOT))) { "Duplicate archive entry" }
                if (item.isDirectory || !format.isImage(name)) continue
                require(members.size < MAX_ARCHIVE_IMAGES) { "Archive has too many disk images" }
                require(item.size in 1..format.limit(name) && item.compressedSize > 0 &&
                    item.size / item.compressedSize <= MAX_EXPANSION_RATIO) { "Disk image exceeds size or expansion limit" }
                byName[item.name] = item
                members.add(Member(item.name, item.size, item.crc))
            }
            require(members.isNotEmpty()) { "No ${format.guestName} disk image in archive" }
            return members
        }

        override fun open(member: Member): InputStream =
            zip.getInputStream(byName[member.name] ?: error("${member.name} is missing from the archive"))

        override fun close() = zip.close()
    }

    private inner class SevenZArchive(file: File) : Archive {
        private val archive = SevenZFile.builder().setFile(file).get()
        private val byName = HashMap<String, org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry>()

        override fun images(checkCancelled: () -> Unit): List<Member> {
            val names = HashSet<String>()
            val members = ArrayList<Member>()
            var inspected = 0
            for (item in archive.entries) {
                checkCancelled()
                require(++inspected <= MAX_ARCHIVE_ENTRIES) { "Archive has too many entries" }
                val name = safeEntryName(item.name)
                require(names.add(name.lowercase(Locale.ROOT))) { "Duplicate archive entry" }
                if (item.isDirectory || !item.hasStream() || !format.isImage(name)) continue
                require(members.size < MAX_ARCHIVE_IMAGES) { "Archive has too many disk images" }
                require(item.size in 1..format.limit(name)) { "Disk image exceeds size limit" }
                byName[item.name] = item
                members.add(Member(item.name, item.size, if (item.hasCrc) item.crcValue else -1))
            }
            require(members.isNotEmpty()) { "No ${format.guestName} disk image in archive" }
            return members
        }

        override fun open(member: Member): InputStream =
            archive.getInputStream(byName[member.name] ?: error("${member.name} is missing from the archive"))

        override fun close() = archive.close()
    }

    @Synchronized private fun cachedArchive(source: Source, force: Boolean, cancelled: AtomicBoolean): File {
        val key = sha256(source.uri.toString().toByteArray()).take(32)
        val file = File(archiveCache, "$key-${source.size}-${source.modified}.${format.extension(source.path)}")
        if (!force && trusted(source) && file.isFile && file.length() == source.size) return file
        require(source.size <= MAX_ARCHIVE_BYTES) { "Archive exceeds size limit" }
        if (source.size >= 0) require(archiveCache.usableSpace > source.size + 16L * 1024 * 1024) {
            "Not enough free space to inspect archive"
        }
        val partial = File(archiveCache, "$key.part")
        partial.delete()
        try {
            context.contentResolver.openInputStream(source.uri)?.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var count = 0L
                    while (true) {
                        checkCancelled(cancelled)
                        val read = input.read(buffer)
                        if (read < 0) break
                        count += read
                        require(count <= MAX_ARCHIVE_BYTES) { "Archive exceeds size limit" }
                        output.write(buffer, 0, read)
                    }
                }
            } ?: error("Unable to open archive")
            if (source.size >= 0) require(partial.length() == source.size) { "Archive changed during read" }
            if (file.exists()) file.delete()
            require(partial.renameTo(file)) { "Unable to cache archive" }
            return file
        } finally { partial.delete() }
    }

    @Synchronized private fun pruneArchives(files: List<Source>) {
        val active = files.filter { format.isArchive(it.path) }.map {
            "${sha256(it.uri.toString().toByteArray()).take(32)}-${it.size}-${it.modified}.${format.extension(it.path)}"
        }.toSet()
        archiveCache.listFiles()?.forEach { file ->
            if (file.name !in active) file.delete()
        }
    }

    private fun contentId(input: InputStream, expectedSize: Long, expectedCrc: Long, cancelled: AtomicBoolean,
                          name: String, output: OutputStream? = null): String {
        val limit = format.limit(name)
        val digest = MessageDigest.getInstance("SHA-256")
        val crc = CRC32()
        val buffer = ByteArray(64 * 1024)
        var count = 0L
        while (true) {
            checkCancelled(cancelled)
            val read = input.read(buffer)
            if (read < 0) break
            count += read
            require(count <= limit) { "Disk image exceeds size limit" }
            digest.update(buffer, 0, read)
            crc.update(buffer, 0, read)
            output?.write(buffer, 0, read)
        }
        require(count > 0) { "Empty disk image" }
        if (expectedSize >= 0) require(count == expectedSize) { "Disk image size changed during read" }
        if (expectedCrc >= 0) require(crc.value == expectedCrc) { "Archive entry checksum mismatch" }
        return "sha256-${format.kind(name)}-v1:${digest.digest().joinToString("") { "%02x".format(it) }}"
    }

    private fun entry(source: Source, member: String?, imageSize: Long, imageCrc: Long, contentId: String?,
                      error: String? = null, key: GameKey = keyOf(source.path, member),
                      catalogId: String? = contentId): DiskImageEntry =
        DiskImageEntry(key.path, source.uri.toString(), source.path, member, source.size, source.modified,
            imageSize, imageCrc, contentId, error, catalogId, label(member ?: source.path))

    /** An image's label; a folder or archive that failed whole is labeled as an image of none. */
    private fun label(name: String) = format.kind(name)?.let(format.labels::getValue) ?: "Disk image"

    /** An image's key: its path in the ROM folder, and its path inside an archive. */
    private fun keyOf(path: String, member: String?) = member?.let { GameKey.inArchive(path, it) } ?: GameKey(path)

    private fun trusted(source: Source) = source.size >= 0 && source.modified > 0
    private fun sameSource(old: DiskImageEntry, source: Source) =
        old.sourceSize == source.size && old.sourceModified == source.modified
    private fun sameFingerprint(old: DiskImageEntry, item: DiskImageEntry) =
        sameSource(old, Source(Uri.parse(item.uri), item.path, item.sourceSize, item.sourceModified)) &&
            old.imageSize == item.imageSize && old.imageCrc == item.imageCrc

    private fun safeEntryName(name: String): String {
        val normalized = name.replace('\\', '/').removeSuffix("/")
        require(normalized.isNotBlank() && !normalized.startsWith('/') &&
            normalized.length <= 4096 && normalized.split('/').none(String::isEmpty) &&
            !normalized.contains(':') && !normalized.contains('\u0000') &&
            normalized.split('/').none { it == ".." || it == "." }) { "Unsafe archive entry path" }
        return normalized
    }

    private fun writeCacheRecord(output: java.io.DataOutputStream, entry: DiskImageEntry) = with(output) {
        writeText(entry.id); writeText(entry.uri); writeText(entry.path); writeOptionalText(entry.zipEntry)
        writeLong(entry.sourceSize); writeLong(entry.sourceModified)
        writeLong(entry.imageSize); writeLong(entry.imageCrc)
        writeOptionalText(entry.contentId); writeOptionalText(entry.error); writeOptionalText(entry.catalogId)
    }

    private fun readCacheRecord(input: java.io.DataInputStream) = with(input) {
        val id = readText()
        val uri = readText()
        val path = readText()
        val member = readOptionalText()
        DiskImageEntry(id, uri, path, member, readLong(), readLong(), readLong(), readLong(),
            readOptionalText(), readOptionalText(), readOptionalText(), label(member ?: path))
    }

    companion object {
        private const val MEDIA_FORMATS_VERSION = 1
        private const val MAX_DOCUMENTS = 50_000
        private const val MAX_ARCHIVE_ENTRIES = 50_000
        private const val MAX_ARCHIVE_IMAGES = 1_000
        private const val MAX_ARCHIVE_BYTES = 2L * 1024 * 1024 * 1024
        private const val MAX_EXPANSION_RATIO = 1000L
        private const val MAX_STORE_BYTES = 16 * 1024 * 1024
        private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }
        private fun checkCancelled(cancelled: AtomicBoolean) {
            if (cancelled.get()) throw CancellationException("Cancelled")
        }
    }
}
