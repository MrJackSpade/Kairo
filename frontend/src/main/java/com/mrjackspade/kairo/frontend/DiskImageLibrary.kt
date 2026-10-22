package com.mrjackspade.kairo.frontend

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.json.JSONObject
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.CRC32
import java.util.zip.ZipFile

/**
 * A disk image: loose in the games folder, or a member of a ZIP or 7z archive there. A descriptor
 * (such as a CD's .ccd) has its track data beside it under its own name, listed in [companions].
 */
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
    /** The other files the image reads: a CD's track data, by document URI or by archive member. */
    val companions: List<String> = emptyList(),
    override val catalogId: String? = contentId,
    /** What the library calls the image, from its [DiskImageLibrary.Format]. */
    override val mediaLabel: String = "Disk image",
    /** False for an archive with no disk image in it, such as one of manuals and scans. */
    override val holdsGame: Boolean = true
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
 * The library's copy of a disk a game names by content ID, preferring one from [source]'s own
 * archive or folder; null when the library has none. A disk keeps the catalog record it was first
 * scanned with through later edits, so its catalog ID matches too.
 */
fun mediaFor(contentId: String, source: DiskImageEntry, entries: List<DiskImageEntry>,
             kind: (DiskImageEntry) -> Boolean = { true }): DiskImageEntry? {
    val candidates = entries.filter { it.playable && kind(it) && (it.contentId == contentId || it.catalogId == contentId) }
    return candidates.firstOrNull { sameSource(source, it) } ?: candidates.firstOrNull()
}

/**
 * A product's disk images in its games folder: loose, or inside ZIP and 7z archives. Each image is
 * named by the SHA-256 of its bytes (`sha256-<kind>-v1:<hex>`, the catalog's content IDs; a
 * descriptor's ID hashes its track data), and a game plays from an app-private working copy that
 * keeps what it writes to its disks.
 */
class DiskImageLibrary(private val context: Context, private val format: Format,
                       private val cache: Cache = Cache()) : MachineLibrary<DiskImageEntry> {
    /** The images a product reads, by file extension. */
    class Format(
        /** Image extensions (lower case) and the content ID kind each is hashed as, such as "fd". */
        val kinds: Map<String, String>,
        /** The library's label for each kind, such as "Floppy disk". */
        val labels: Map<String, String>,
        /** The largest image of each kind; for a descriptor, its track data. */
        val maxBytes: Map<String, Long>,
        /** The machine's name, for messages such as "No PC-88 disk image in archive". */
        val guestName: String,
        /**
         * Descriptor extensions and the extensions their track data may have beside them under the
         * descriptor's name, first match first: ccd to img, cue to bin then img.
         */
        val data: Map<String, List<String>> = emptyMap(),
        /** Descriptors skipped when another descriptor of the same name describes the same disc: cue to ccd. */
        val supersededBy: Map<String, Set<String>> = emptyMap()
    ) {
        init {
            require(kinds.values.all { it in labels && it in maxBytes })
            require(data.keys.all { it in kinds } && supersededBy.keys.all { it in kinds })
        }
        fun extension(name: String): String = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        fun kind(name: String): String? = kinds[extension(name)]
        fun isImage(name: String) = kind(name) != null
        fun isArchive(name: String) = extension(name) in ARCHIVES
        /** Whether [name] is track data, read only through its descriptor. */
        fun isData(name: String) = !isImage(name) && data.values.any { extension(name) in it }
        fun isDescriptor(name: String) = extension(name) in data
        /** The names [name]'s track data may have, in order; just [name] for an image without a descriptor. */
        fun dataNames(name: String): List<String> =
            data[extension(name)]?.map { name.substringBeforeLast('.') + ".$it" } ?: listOf(name)
        /** [name]'s track data among [present] (lower-case names to items), or null when none is there. */
        fun <T> dataOf(name: String, present: Map<String, T>): T? =
            dataNames(name).firstNotNullOfOrNull { present[it.lowercase(Locale.ROOT)] }
        /** Whether a descriptor of [name]'s name among [present] describes its disc instead. */
        fun superseded(name: String, present: Set<String>): Boolean =
            supersededBy[extension(name)].orEmpty().any { "${name.substringBeforeLast('.')}.$it".lowercase(Locale.ROOT) in present }
        private val idPattern = Regex("sha256-(?:${kinds.values.distinct().joinToString("|")})-v1:[0-9a-f]{64}")
        fun validId(value: String) = idPattern.matches(value)
        fun limit(name: String): Long = maxBytes.getValue(kind(name) ?: error("$name is not a $guestName disk image"))

        companion object { private val ARCHIVES = setOf("zip", "7z") }
    }

    /**
     * Where the library keeps its scan. [version] is the record layout; [earlierRecords] read the
     * layouts before it and [legacy] the JSON cache of the first releases, for products that had them.
     * [mediaFormatsVersion] changes when the formats do, so archives are inspected again.
     */
    class Cache(
        val file: String = "library-v2.bin",
        val mediaFormatsVersion: Int = 1,
        val legacy: VersionedLibraryCache.Legacy<DiskImageEntry>? = null,
        val earlierRecords: Map<Int, (DataInputStream) -> DiskImageEntry> = emptyMap()
    )

    private val storeCache = VersionedLibraryCache(File(context.filesDir, cache.file), CACHE_LAYOUT,
        MAX_STORE_BYTES, MAX_DOCUMENTS, ::writeCacheRecord, ::readCacheRecord,
        cache.legacy?.let { legacy ->
            VersionedLibraryCache.Legacy(legacy.file, legacy.version, legacy.collectionName) { labeled(legacy.decode(it)) }
        }, cache.earlierRecords.mapValues { (_, read) -> { input: DataInputStream -> labeled(read(input)) } } +
            (4 to ::readCacheRecordV4))
    private val scanPipeline by lazy {
        LibraryScanPipeline(storeCache, DiskImageEntry::id, ::planScan, ::inspectSource, ::failedSource,
            { source, index, total -> "Scanning ${index + 1}/$total: ${source.path}" },
            displayOrder = { entries -> entries.sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER) { it.path + (it.zipEntry ?: "") }) },
            metadata = { JSONObject().put("mediaFormatsVersion", cache.mediaFormatsVersion) },
            afterCommit = { plan -> pruneArchives(plan.work) })
    }
    private val archiveCache = File(context.cacheDir, "rom-archives").apply { mkdirs() }
    /** The working copies games play from, named by game key. */
    val workingDirectory = File(context.filesDir, "media").apply {
        mkdirs()
        listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() }
    }

    override val hashCount: Int get() = scanPipeline.hashCount

    override fun cached(tree: Uri): List<DiskImageEntry> = scanPipeline.cached(tree)

    override fun store(tree: Uri, entries: List<DiskImageEntry>) = scanPipeline.store(tree, entries)

    override fun scan(tree: Uri, forceHash: Boolean, cancelled: AtomicBoolean,
                      progress: (String) -> Unit): List<DiskImageEntry> = scanPipeline.scan(tree, forceHash, cancelled, progress)

    /** Delete the source document shared by every image entry from that file, and a loose descriptor's track data. */
    override fun deleteSource(entry: DiskImageEntry, entries: List<DiskImageEntry>) {
        val sourceUri = Uri.parse(entry.uri)
        require(DocumentsContract.isDocumentUri(context, sourceUri)) { "This game is not a removable library document" }
        require(DocumentsContract.deleteDocument(context.contentResolver, sourceUri)) {
            "The document provider could not delete the game file"
        }
        if (entry.zipEntry == null) entry.companions.forEach { companion ->
            require(DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(companion))) {
                "The document provider could not delete the track data of ${entry.displayName}"
            }
        }
        val archiveKey = sha256(entry.uri.toByteArray()).take(32)
        archiveCache.listFiles()?.filter { it.name.startsWith("$archiveKey-") || it.name == "$archiveKey.part" }
            ?.forEach { it.delete() }
        entries.filter { it.uri == entry.uri }.forEach(::resetWorkingImage)
    }

    /** The working copy the game plays from, with the saves written into it; the next launch copies the source again. */
    override fun resetWorkingImage(entry: DiskImageEntry) {
        val file = workingImage(entry)
        for (copy in setOf(file) + format.dataNames(file.name).map { File(workingDirectory, it) })
            check(!copy.exists() || copy.delete()) { "Could not remove the working copy of ${entry.displayName}" }
    }

    override fun hasWorkingImage(entry: DiskImageEntry) = workingImage(entry).isFile

    /** The working copy of [entry], whether or not it exists yet. */
    fun workingImage(entry: DiskImageEntry) =
        File(workingDirectory, "${entry.key.storageName}.${format.extension(entry.displayName)}")

    /** The library entry whose working copy is [path], for a disk the machine holds. */
    override fun entryForWorkingImage(path: String, entries: List<DiskImageEntry>): DiskImageEntry? =
        entries.firstOrNull { workingImage(it).absolutePath == path }

    /** Inspect one launcher-provided document without requiring a selected ROM tree. */
    override fun inspectExternal(file: ExternalGameFile, cancelled: AtomicBoolean): List<DiskImageEntry> {
        val source = Source(file.uri, file.name, file.size, file.modified)
        require(!format.isDescriptor(file.name)) {
            "${file.name} needs its track data; open its folder or an archive that holds both"
        }
        if (format.isImage(file.name)) {
            val candidate = entry(source, null, file.size, -1, null, key = GameKey(file.uri.toString()))
            val id = context.contentResolver.openInputStream(file.uri)?.use {
                contentId(it, file.size, -1, cancelled, file.name)
            } ?: error("Unable to open ${file.name}")
            return listOf(candidate.copy(contentId = id, catalogId = id))
        }
        require(format.isArchive(file.name)) { "Unsupported ${format.guestName} game file: ${file.name}" }
        return openArchive(cachedArchive(source, false, cancelled)).use { archive ->
            val images = archive.images { checkCancelled(cancelled) }
            require(images.isNotEmpty()) { "No ${format.guestName} disk image in ${file.name}" }
            images.map { image ->
                val data = image.data ?: error(image.missing())
                val id = archive.open(data).use { contentId(it, data.size, data.crc, cancelled, image.name) }
                entry(source, image.name, data.size, data.crc, id, companions = image.companions,
                    key = GameKey.inArchive(file.uri.toString(), image.name))
            }
        }
    }

    private fun planScan(tree: Uri, cancelled: AtomicBoolean,
                         progress: (String) -> Unit): LibraryScanPipeline.Plan<Source, DiskImageEntry> {
        val result = DocumentTreeWalker(context.contentResolver).scan(tree, cancelled,
            { name -> format.isImage(name) || format.isArchive(name) || format.isData(name) }, progress)
        val files = result.files.map { Source(it.uri, it.path, it.size, it.modified) }
        // Track data is read through its descriptor; data without one is not an image.
        val byPath = files.associateBy { it.path.lowercase(Locale.ROOT) }
        return LibraryScanPipeline.Plan(files.filter { !format.isData(it.path) && !format.superseded(it.path, byPath.keys) }
            .map { source ->
                if (format.isDescriptor(source.path)) source.copy(data = format.dataOf(source.path, byPath))
                else source
            }, initialEntries = result.folders.map { folder ->
            entry(Source(folder.uri, folder.path, -1, 0), null, -1, -1, null, "Unreadable folder: ${folder.message}")
        })
    }

    /** The content each key was first scanned with stays its catalog record through later edits. */
    private fun inspectSource(source: Source, scan: LibraryScanPipeline.ScanContext<DiskImageEntry>): List<DiskImageEntry> =
        inspectContent(source, scan).map { it.copy(catalogId = scan.earlier[it.id]?.catalogId ?: it.contentId) }

    private fun inspectContent(source: Source, scan: LibraryScanPipeline.ScanContext<DiskImageEntry>): List<DiskImageEntry> {
        if (format.isArchive(source.path)) {
            val oldEntries = scan.prior.values.filter { it.uri == source.uri.toString() }
            val formatsCurrent = (scan.previous?.root?.optInt("mediaFormatsVersion", 1) ?: 0) >= cache.mediaFormatsVersion
            if (!scan.forceHash && formatsCurrent && trusted(source) && oldEntries.isNotEmpty() &&
                oldEntries.all { sameSource(it, source) && it.error == null }) return oldEntries
            return openArchive(cachedArchive(source, scan.forceHash, scan.cancelled)).use { archive ->
                val images = archive.images(scan::checkCancelled)
                if (images.isEmpty()) return listOf(entry(source, null, source.size, -1, null).copy(holdsGame = false))
                images.map { image ->
                    scan.checkCancelled()
                    val data = image.data
                    val candidate = entry(source, image.name, data?.size ?: -1, data?.crc ?: -1, null,
                        companions = image.companions)
                    val old = scan.prior[candidate.id]
                    try {
                        requireNotNull(data) { image.missing() }
                        val id = if (!scan.forceHash && trusted(source) && old != null &&
                            sameFingerprint(old, candidate) && old.contentId?.let(format::validId) == true) old.contentId
                        else {
                            scan.progress("Hashing ${image.name}")
                            scan.hashStarted()
                            archive.open(data).use { contentId(it, data.size, data.crc, scan.cancelled, image.name) }
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
        if (format.isDescriptor(source.path)) {
            val data = source.data ?: error("The track data of ${source.path.substringAfterLast('/')} is missing")
            val candidate = entry(source, null, data.size, -1, null, companions = listOf(data.uri.toString()))
            val old = scan.prior[candidate.id]
            val id = if (!scan.forceHash && trusted(source) && trusted(data) && old != null &&
                sameFingerprint(old, candidate) && old.contentId?.let(format::validId) == true) old.contentId
            else {
                scan.progress("Hashing ${data.path}")
                scan.hashStarted()
                context.contentResolver.openInputStream(data.uri)?.use {
                    contentId(it, data.size, -1, scan.cancelled, source.path)
                } ?: error("Unable to open ${data.path}")
            }
            return listOf(candidate.copy(contentId = id))
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
     * time. The copy is verified against the entry's content ID. A descriptor's track data is copied
     * beside it, under the name the core derives from the descriptor's.
     */
    @Synchronized override fun prepare(entry: DiskImageEntry, cancelled: AtomicBoolean, progress: (String) -> Unit): File {
        require(entry.playable) { "This entry is not ready to launch" }
        // The working copy holds the game's in-disk saves, so it stays when the source is edited.
        val file = workingImage(entry)
        val descriptor = format.isDescriptor(entry.displayName)
        // The track data keeps its extension beside the descriptor, where the core looks for it.
        val data = if (descriptor) File(workingDirectory,
            "${file.name.substringBeforeLast('.')}.${format.extension(entry.companions.single())}") else file
        if (file.isFile && file.length() > 0 && data.isFile && data.length() > 0) return file
        if (entry.imageSize > 0) require(workingDirectory.usableSpace > entry.imageSize + 16L * 1024 * 1024) {
            "Not enough free space for this disk image"
        }
        val partialData = File(workingDirectory, "${data.name}.part")
        val partialDescriptor = File(workingDirectory, "${file.name}.part")
        partialData.delete()
        partialDescriptor.delete()
        try {
            progress("Preparing ${entry.displayName}")
            if (entry.zipEntry == null) {
                val dataUri = if (descriptor) Uri.parse(entry.companions.single()) else Uri.parse(entry.uri)
                context.contentResolver.openInputStream(dataUri)?.use { input ->
                    partialData.outputStream().use { output -> copyVerified(entry, input, -1, cancelled, output) }
                } ?: error("Unable to open disk image")
                if (descriptor) context.contentResolver.openInputStream(Uri.parse(entry.uri))?.use { input ->
                    partialDescriptor.outputStream().use { input.copyTo(it) }
                } ?: error("Unable to open ${entry.displayName}")
            } else {
                openArchive(archive(entry, cancelled)).use { archive ->
                    val image = archive.images { checkCancelled(cancelled) }.firstOrNull { it.name == entry.zipEntry }
                        ?: error("${entry.zipEntry} is missing from the archive")
                    val member = image.data ?: error(image.missing())
                    archive.open(member).use { input ->
                        partialData.outputStream().use { output -> copyVerified(entry, input, member.crc, cancelled, output) }
                    }
                    if (descriptor) archive.open(image.entry).use { input ->
                        partialDescriptor.outputStream().use { input.copyTo(it) }
                    }
                }
            }
            require(partialData.renameTo(data)) { "Unable to save working disk image" }
            if (descriptor) require(partialDescriptor.renameTo(file)) { "Unable to save ${entry.displayName}" }
            return file
        } catch (error: Exception) {
            partialData.delete()
            partialDescriptor.delete()
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
    fun archive(entry: DiskImageEntry, cancelled: AtomicBoolean): File {
        require(entry.zipEntry != null) { "${entry.displayName} is not in an archive" }
        return cachedArchive(Source(Uri.parse(entry.uri), entry.path, entry.sourceSize, entry.sourceModified),
            false, cancelled)
    }

    /** A document to inspect; a descriptor also carries its track data. */
    private data class Source(val uri: Uri, val path: String, val size: Long, val modified: Long,
                              val data: Source? = null)

    /** An archive member: its normalized path, size, and CRC-32 when the archive records one. */
    private class Member(val name: String, val size: Long, val crc: Long)

    /** An image in an archive, with the member its bytes come from: itself, or a descriptor's track data. */
    private inner class Image(val entry: Member, val data: Member?) {
        val name: String get() = entry.name
        val companions: List<String> get() = if (data == null || data === entry) emptyList() else listOf(data.name)
        fun missing() = "The track data of $name is missing from the archive"
    }

    /** A ZIP or 7z archive's members. */
    private abstract inner class Archive : Closeable {
        protected abstract fun members(checkCancelled: () -> Unit): List<Member>
        abstract fun open(member: Member): InputStream

        /**
         * The archive's images, none for an archive of other files; a descriptor whose track data
         * is missing has none, and fails alone.
         */
        fun images(checkCancelled: () -> Unit): List<Image> {
            val members = members(checkCancelled)
            val byName = members.associateBy { it.name.lowercase(Locale.ROOT) }
            val images = members.filter { format.isImage(it.name) && !format.superseded(it.name, byName.keys) }
            require(images.size <= MAX_ARCHIVE_IMAGES) { "Archive has too many disk images" }
            return images.map { member ->
                val data = if (format.isDescriptor(member.name)) format.dataOf(member.name, byName) else member
                if (data != null) require(data.size in 1..format.limit(member.name)) { "Disk image exceeds size limit" }
                Image(member, data)
            }
        }
    }

    private fun openArchive(file: File): Archive =
        if (file.name.endsWith(".7z")) SevenZArchive(file) else ZipArchive(file)

    private inner class ZipArchive(file: File) : Archive() {
        private val zip = ZipFile(file)
        private val byName = HashMap<String, java.util.zip.ZipEntry>()

        override fun members(checkCancelled: () -> Unit): List<Member> {
            val names = HashSet<String>()
            val members = ArrayList<Member>()
            var inspected = 0
            for (item in zip.entries()) {
                checkCancelled()
                require(++inspected <= MAX_ARCHIVE_ENTRIES) { "Archive has too many entries" }
                val name = safeEntryName(item.name)
                require(names.add(name.lowercase(Locale.ROOT))) { "Duplicate archive entry" }
                if (item.isDirectory || !(format.isImage(name) || format.isData(name))) continue
                require(item.compressedSize > 0 && item.size / item.compressedSize <= MAX_EXPANSION_RATIO) {
                    "Disk image exceeds expansion limit"
                }
                byName[item.name] = item
                members.add(Member(item.name, item.size, item.crc))
            }
            return members
        }

        override fun open(member: Member): InputStream =
            zip.getInputStream(byName[member.name] ?: error("${member.name} is missing from the archive"))

        override fun close() = zip.close()
    }

    private inner class SevenZArchive(file: File) : Archive() {
        private val archive = SevenZFile.builder().setFile(file).get()
        private val byName = HashMap<String, org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry>()

        override fun members(checkCancelled: () -> Unit): List<Member> {
            val names = HashSet<String>()
            val members = ArrayList<Member>()
            var inspected = 0
            for (item in archive.entries) {
                checkCancelled()
                require(++inspected <= MAX_ARCHIVE_ENTRIES) { "Archive has too many entries" }
                val name = safeEntryName(item.name)
                require(names.add(name.lowercase(Locale.ROOT))) { "Duplicate archive entry" }
                if (item.isDirectory || !item.hasStream() || !(format.isImage(name) || format.isData(name))) continue
                byName[item.name] = item
                members.add(Member(item.name, item.size, if (item.hasCrc) item.crcValue else -1))
            }
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

    /** The content ID of [name]'s bytes from [input]: its track data for a descriptor. */
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

    /** A loose descriptor's modification time is its newest file's, so changed track data rehashes. */
    private fun entry(source: Source, member: String?, imageSize: Long, imageCrc: Long, contentId: String?,
                      error: String? = null, companions: List<String> = emptyList(),
                      key: GameKey = keyOf(source.path, member), catalogId: String? = contentId): DiskImageEntry =
        DiskImageEntry(key.path, source.uri.toString(), source.path, member, source.size,
            maxOf(source.modified, source.data?.modified ?: 0), imageSize, imageCrc, contentId, error, companions,
            catalogId, label(member ?: source.path))

    /** An image's label; a folder or archive that failed whole is labeled as an image of none. */
    private fun label(name: String) = format.kind(name)?.let(format.labels::getValue) ?: "Disk image"

    /** [entry] with the label its formats give it, as for one an earlier cache layout read. */
    private fun labeled(entry: DiskImageEntry) = entry.copy(mediaLabel = label(entry.zipEntry ?: entry.path))

    /** An image's key: its path in the games folder, and its path inside an archive. */
    fun keyOf(path: String, member: String?) = member?.let { GameKey.inArchive(path, it) } ?: GameKey(path)

    private fun trusted(source: Source) = source.size >= 0 && source.modified > 0
    private fun sameSource(old: DiskImageEntry, source: Source) =
        old.sourceSize == source.size && old.sourceModified == maxOf(source.modified, source.data?.modified ?: 0)
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

    private fun writeCacheRecord(output: DataOutputStream, entry: DiskImageEntry) = with(output) {
        writeText(entry.id); writeText(entry.uri); writeText(entry.path); writeOptionalText(entry.zipEntry)
        writeLong(entry.sourceSize); writeLong(entry.sourceModified)
        writeLong(entry.imageSize); writeLong(entry.imageCrc)
        writeOptionalText(entry.contentId); writeOptionalText(entry.error)
        writeInt(entry.companions.size); entry.companions.forEach { writeText(it) }
        writeOptionalText(entry.catalogId)
        writeBoolean(entry.holdsGame)
    }

    private fun readCacheRecord(input: DataInputStream) = readCacheRecordV4(input).copy(holdsGame = input.readBoolean())

    /** Layout 4, from before archives without a game were remembered. */
    private fun readCacheRecordV4(input: DataInputStream) = with(input) {
        labeled(DiskImageEntry(readText(), readText(), readText(), readOptionalText(), readLong(), readLong(),
            readLong(), readLong(), readOptionalText(), readOptionalText(), List(readInt()) { readText() },
            readOptionalText()))
    }

    companion object {
        /** The cache record layout: 5 records whether an archive holds a game. */
        private const val CACHE_LAYOUT = 5
        private const val MAX_DOCUMENTS = 50_000
        private const val MAX_ARCHIVE_ENTRIES = 50_000
        private const val MAX_ARCHIVE_IMAGES = 1_000
        private const val MAX_ARCHIVE_BYTES = 8L * 1024 * 1024 * 1024
        private const val MAX_EXPANSION_RATIO = 1000L
        private const val MAX_STORE_BYTES = 16 * 1024 * 1024
        private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }
        private fun checkCancelled(cancelled: AtomicBoolean) {
            if (cancelled.get()) throw CancellationException("Cancelled")
        }
    }
}
