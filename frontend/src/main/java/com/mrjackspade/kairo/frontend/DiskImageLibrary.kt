package com.mrjackspade.kairo.frontend

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.os.ParcelFileDescriptor
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import org.json.JSONObject
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.CRC32

/**
 * A file of a program's folder ([DiskImageEntry.folder]): its path relative to the program's folder,
 * and where it comes from, a document URI for a loose program or an archive member for one in an archive.
 */
data class FolderFile(val name: String, val source: String)

/**
 * A disk image: loose in the games folder, or a member of a ZIP or 7z archive there. A descriptor
 * (such as a CD's .ccd) has its track data beside it under its own name, listed in [companions]. A
 * program (a kind in [DiskImageLibrary.Format.programs]) runs from its folder, whose other files
 * are listed in [folder].
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
    val mediaText: String = "Disk image",
    /** False for an archive with no disk image in it, such as one of manuals and scans. */
    override val holdsGame: Boolean = true,
    /** A program's folder: every other file in it and below it, which the program may read. */
    val folder: List<FolderFile> = emptyList(),
    /** The game picked from a collection; never cached, a launch picks it. */
    override val member: String? = null
) : LibraryItem {
    /** Resolved from the [DiskImageLibrary.Format] whenever the library is read, so it is fixed text here. */
    override val mediaLabel: UiText get() = UiText.fixed(mediaText)
    override val displayName: String get() = (zipEntry ?: path).substringAfterLast('/').substringAfterLast('\\')
    override val playable: Boolean get() = contentId != null && error == null
}

/** Entries from the same archive, or from the same folder for a loose image. */
fun sameSource(entry: LibraryItem, other: LibraryItem): Boolean =
    if (entry.zipEntry != null) other.zipEntry != null && other.uri == entry.uri
    else other.zipEntry == null &&
        other.path.substringBeforeLast('/', "") == entry.path.substringBeforeLast('/', "")

/**
 * The library's copy of a disk a game names by content ID, preferring one from [source]'s own
 * archive or folder; null when the library has none. A disk keeps the catalog record it was first
 * scanned with through later edits, so its catalog ID matches too.
 */
fun <E : LibraryItem> mediaFor(contentId: String, source: E, entries: List<E>, kind: (E) -> Boolean = { true }): E? {
    val candidates = entries.filter { it.playable && kind(it) && (it.contentId == contentId || it.catalogId == contentId) }
    return candidates.firstOrNull { sameSource(source, it) } ?: candidates.firstOrNull()
}

/**
 * A disk a game uses beside the one it starts from, the one rule every app finds it by: a hard disk
 * game's CD, a CD game's floppy, the second disk of a two-disk set. It is the one its catalog names
 * by [catalogId], which the library must have ([MissingDisk] saying [missing] when it does not),
 * else the one other disk of that [kind] in [source]'s archive or folder; null when there is none,
 * or several to choose from.
 */
fun companionFor(catalogId: String?, source: DiskImageEntry, entries: List<DiskImageEntry>, missing: UiText,
                 kind: (DiskImageEntry) -> Boolean): DiskImageEntry? {
    catalogId?.let { id ->
        return mediaFor(id, source, entries, kind) ?: throw MissingDisk(missing)
    }
    return entries.filter { it.playable && kind(it) && it.id != source.id && sameSource(source, it) }.singleOrNull()
}

/** The disk named [label], such as "CD", that a game needs is not in the library, as [MissingDisk] says it. */
fun missingDisk(label: UiText): UiText = UiText.res(R.string.kairo_companion_missing, label)

/** A disk a game needs is not in the library; [text] says so to the player. */
class MissingDisk(val text: UiText) : IllegalStateException()

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
        val labels: Map<String, UiText>,
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
        val supersededBy: Map<String, Set<String>> = emptyMap(),
        /**
         * Kinds that are programs rather than disk images: one runs from its folder, which the
         * machine reads as a drive, so its entry lists the folder's files and plays from a copy of
         * the whole folder.
         */
        val programs: Set<String> = emptySet()
    ) {
        init {
            require(kinds.values.all { it in labels && it in maxBytes })
            require(data.keys.all { it in kinds } && supersededBy.keys.all { it in kinds })
            require(programs.all { it in kinds.values })
        }
        fun extension(name: String): String = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        fun kind(name: String): String? = kinds[extension(name)]
        fun isImage(name: String) = kind(name) != null
        fun isProgram(name: String) = kind(name) in programs
        /** Whether a scan lists every file: a program's folder holds files of any kind. */
        val listsEveryFile: Boolean get() = programs.isNotEmpty()
        fun isArchive(name: String) = extension(name) in ARCHIVES
        /** Whether [name] is track data, read only through its descriptor. */
        fun isData(name: String) = !isImage(name) && data.values.any { extension(name) in it }
        /** How a descriptor names its track data. */
        val descriptors = DiscDescriptors(data)
        fun isDescriptor(name: String) = descriptors.isDescriptor(name)
        fun dataNames(name: String): List<String> = descriptors.dataNames(name)
        fun <T> dataOf(name: String, present: Map<String, T>): T? = descriptors.dataOf(name, present)
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
            (4 to ::readCacheRecordV4) + (5 to ::readCacheRecordV5))
    /** The games folder's listings, so a launch lists only the folders that changed (ScanDepth). */
    private val folderListings = FolderListings(File(context.filesDir, "folder-listings-v1.json"))
    private val scanPipeline by lazy {
        LibraryScanPipeline(context, storeCache, DiskImageEntry::id, ::planScan, ::inspectSource, ::failedSource,
            Source::path, ::known, DiskImageEntry::path,
            displayOrder = { entries -> entries.sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER) { it.path + (it.zipEntry ?: "") }) },
            metadata = { JSONObject().put("mediaFormatsVersion", cache.mediaFormatsVersion) })
    }

    init {
        // Archives are read in place; earlier releases kept a full copy of each one here.
        File(context.cacheDir, "rom-archives").deleteRecursively()
    }
    /**
     * The working copies games play from, at each game's save path (Kairo#70): an image under
     * the game's own name (its extension in lower case, as the cores have always been given it),
     * a descriptor's track data beside it, or a program's folder `<savePath>.folder`. Before
     * v1.4.1 each was named by its game's hash name ([hashNamedWorkingCopies] renames them).
     */
    val workingDirectory = File(context.filesDir, "media").apply {
        mkdirs()
        walkTopDown().filter { it.name.endsWith(".part") }.toList().forEach { it.deleteRecursively() }
    }

    override val hashCount: Int get() = scanPipeline.hashCount

    override fun cached(tree: Uri): List<DiskImageEntry> = scanPipeline.cached(tree)

    override fun store(tree: Uri, entries: List<DiskImageEntry>) = scanPipeline.store(tree, entries)

    override fun scan(tree: Uri, depth: ScanDepth, cancelled: AtomicBoolean,
                      progress: (String) -> Unit): List<DiskImageEntry> = scanPipeline.scan(tree, depth, cancelled, progress)

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
        entries.filter { it.uri == entry.uri }.forEach(::resetWorkingImage)
    }

    /** The working copy the game plays from, with the saves written into it; the next launch copies the source again. */
    override fun resetWorkingImage(entry: DiskImageEntry) {
        for ((_, copy) in workingFiles(entry, null))
            check(!copy.exists() || copy.deleteRecursively()) { "Could not remove the working copy of ${entry.displayName}" }
    }

    override fun hasWorkingImage(entry: DiskImageEntry) = workingImage(entry).isFile
    override fun withMember(entry: DiskImageEntry, member: String) = entry.copy(member = member)

    override fun withError(entry: DiskImageEntry, message: String) = entry.copy(error = message)

    /** The working copy of [entry], whether or not it exists yet: a program's is in its folder's copy. */
    fun workingImage(entry: DiskImageEntry): File =
        if (format.isProgram(entry.displayName)) File(programFolder(entry), entry.displayName)
        else workingFile(withoutExtension(entry.key.savePath) + "." + format.extension(entry.displayName))

    /**
     * [path] without its last segment's extension. A disk picked from outside the library is keyed
     * by its document URI, whose last segment often has no dot (`…downloads.documents/document/msf:30`):
     * cut at the path's last dot instead, every disk from that provider shared one working copy, and
     * the first one picked was the disk each later pick read (Kairo98#128).
     */
    private fun withoutExtension(path: String): String {
        val name = path.substringAfterLast('/')
        return path.dropLast(name.length) + name.substringBeforeLast('.')
    }

    /** The working copy of a program's folder, which the machine reads as a drive. */
    private fun programFolder(entry: DiskImageEntry) = workingFile("${entry.key.savePath}.folder")

    private fun workingFile(path: String) = File(workingDirectory, SaveLocation.segments(path).joinToString(File.separator))

    /**
     * [entry]'s working files, each with where it was when its game's key had [hashName] (before
     * v1.4.1), or null for none: a program's folder, else a descriptor's possible track data and
     * then the image itself, last.
     */
    private fun workingFiles(entry: DiskImageEntry, hashName: String?): List<Pair<File?, File>> {
        if (format.isProgram(entry.displayName)) return listOf(hashName?.let { File(workingDirectory, it) } to programFolder(entry))
        val image = workingImage(entry)
        val extension = format.extension(entry.displayName)
        return format.data[extension].orEmpty().map { data ->
            hashName?.let { File(workingDirectory, "$it.$data") } to File(image.parentFile, "${image.name.substringBeforeLast('.')}.$data")
        } + (hashName?.let { File(workingDirectory, "$it.$extension") } to image)
    }

    /** The hash names [entry]'s working copy had: its key's, and the URI key's v1.4.0 gave a file a frontend sent. */
    private fun earlierNames(entry: DiskImageEntry) = listOf(entry.key.hashName,
        (entry.zipEntry?.let { GameKey.inArchive(entry.uri, it) } ?: GameKey(entry.uri)).hashName)

    override fun statePaths(entries: List<DiskImageEntry>): Map<String, String> = buildMap {
        for (entry in entries) for (name in earlierNames(entry)) for ((earlier, now) in workingFiles(entry, name)) {
            put(earlier!!.absolutePath, now.absolutePath)
            // A program's folder is a drive; its files are named inside it.
            if (format.isProgram(entry.displayName)) put(File(earlier, entry.displayName).absolutePath, workingImage(entry).absolutePath)
        }
    }

    /**
     * Working copies named by their game's hash name before v1.4.1, for [GamePathMigration]:
     * each claim's earlier files move to their game's path by renaming, track data before the
     * disk so a move cut short leaves the disk to show there is more. A game that already has a
     * working copy at its path is a conflict the player settles.
     */
    override fun hashNamedWorkingCopies() = object : GamePathMigration.Store<DiskImageEntry> {
        /** The hash names of the working copies here, as last read. */
        private var names = emptySet<String>()

        override fun refresh(claims: List<GamePathMigration.Claim<DiskImageEntry>>, every: Boolean) {
            names = if (every) workingDirectory.list().orEmpty().map { it.substringBefore('.') }.filter { GameKey.HASH_NAME.matches(it) }.toSet()
                else claims.filter { claim -> files(claim).isNotEmpty() }.map { it.from }.toSet()
        }

        override fun has(hashName: String) = hashName in names

        /** The earlier files present, while the disk is: once it has moved, everything has. */
        private fun files(claim: GamePathMigration.Claim<DiskImageEntry>): List<Pair<File, File>> {
            val files = workingFiles(claim.item, claim.from).map { (earlier, now) -> earlier!! to now }
            if (!files.last().first.exists()) return emptyList()
            return files.filter { it.first.exists() }
        }

        override fun conflicts(claim: GamePathMigration.Claim<DiskImageEntry>): List<GamePathMigration.Conflict> {
            val (from, to) = files(claim).lastOrNull() ?: return emptyList()
            if (!to.exists()) return emptyList()
            return listOf(GamePathMigration.Conflict(claim.to, WORKING_COPY, context.getString(R.string.kairo_disk_working_copy), newest(from), newest(to), claim.external))
        }

        override fun move(claim: GamePathMigration.Claim<DiskImageEntry>, keep: Set<GamePathMigration.Conflict>) {
            for ((from, to) in files(claim)) {
                if (keep.isNotEmpty()) {
                    check(from.deleteRecursively()) { "Could not remove the earlier working copy of ${claim.item.displayName}" }
                    continue
                }
                check(to.parentFile!!.isDirectory || to.parentFile!!.mkdirs()) { "Could not create the working copy's folder" }
                check(!to.exists() || to.deleteRecursively()) { "Could not replace the working copy of ${claim.item.displayName}" }
                check(from.renameTo(to)) { "Could not move the working copy of ${claim.item.displayName}" }
            }
            names = names - claim.from
        }

        override fun isEmpty() = names.isEmpty()

        private fun newest(file: File) = file.walkTopDown().filter { it.isFile }.maxOfOrNull { it.lastModified() } ?: file.lastModified()
    }

    /**
     * The library entry whose working copy is [path], for a disk the machine holds. An emulator may
     * report the file by another name for it (MAME gives /data/data/<package> for the
     * /data/user/0/<package> of the app's files), so [path] is taken as the same place under
     * [workingDirectory] whichever name of that folder it starts with.
     */
    override fun entryForWorkingImage(path: String, entries: List<DiskImageEntry>): DiskImageEntry? {
        val canonicalRoot = workingDirectory.canonicalPath + File.separator
        val held = if (path.startsWith(canonicalRoot))
            File(workingDirectory, path.removePrefix(canonicalRoot)).absolutePath else path
        return entries.firstOrNull { workingImage(it).absolutePath == held }
    }

    /** Inspect one launcher-provided document without requiring a selected ROM tree. */
    override fun inspectExternal(file: ExternalGameFile, cancelled: AtomicBoolean): List<DiskImageEntry> {
        val source = Source(file.uri, file.name, file.size, file.modified)
        require(!format.isDescriptor(file.name)) {
            context.getString(R.string.kairo_disk_needs_track_data, file.name)
        }
        require(!format.isProgram(file.name)) {
            context.getString(R.string.kairo_disk_runs_from_folder, file.name)
        }
        if (format.isImage(file.name)) {
            val candidate = entry(source, null, file.size, -1, null, key = GameKey(file.key))
            val id = context.contentResolver.openInputStream(file.uri)?.use {
                contentId(it, file.size, -1, cancelled, file.name)
            } ?: error("Unable to open ${file.name}")
            return listOf(candidate.copy(contentId = id, catalogId = id))
        }
        require(format.isArchive(file.name)) { context.getString(R.string.kairo_disk_unsupported_file, format.guestName, file.name) }
        return openArchive(source).use { archive ->
            val images = archive.images { checkCancelled(cancelled) }
            require(images.isNotEmpty()) { context.getString(R.string.kairo_disk_no_image_in, format.guestName, file.name) }
            images.map { image ->
                val data = image.data ?: error(image.missing())
                val id = archive.open(data).use { contentId(it, data.size, data.crc, cancelled, image.name) }
                entry(source, image.name, data.size, data.crc, id, companions = image.companions,
                    key = GameKey.inArchive(file.key, image.name), folder = image.folderFiles)
            }
        }
    }

    private fun planScan(tree: Uri, depth: ScanDepth, cancelled: AtomicBoolean,
                         progress: (String) -> Unit): LibraryScanPipeline.Plan<Source, DiskImageEntry> {
        val result = DocumentTreeWalker(context).scan(tree, cancelled,
            { name -> format.listsEveryFile || format.isImage(name) || format.isArchive(name) || format.isData(name) },
            progress, folderListings, depth)
        val everyFile = result.files.map { Source(it.uri, it.path, it.size, it.modified) }
        val files = everyFile.filter { format.isImage(it.path) || format.isArchive(it.path) || format.isData(it.path) }
        // Track data is read through its descriptor; data without one is not an image.
        val byPath = files.associateBy { it.path.lowercase(Locale.ROOT) }
        return LibraryScanPipeline.Plan(files.filter { !format.isData(it.path) && !format.superseded(it.path, byPath.keys) }
            .map { source ->
                if (format.isDescriptor(source.path)) source.copy(data = format.dataOf(source.path, byPath))
                else if (format.isProgram(source.path)) source.copy(folder = folderOf(source, everyFile))
                else source
            }, initialEntries = result.folders.map { folder ->
            entry(Source(folder.uri, folder.path, -1, 0), null, -1, -1, null, context.getString(R.string.kairo_disk_unreadable_folder, folder.message))
        })
    }

    /**
     * A loose program's folder: the other files in its folder and below it, by their paths relative
     * to it. A program at the top of the games folder takes only the files beside it, not the
     * other games' folders.
     */
    private fun folderOf(program: Source, everyFile: List<Source>): List<Pair<String, Source>> {
        val folder = program.path.substringBeforeLast('/', "")
        return everyFile.filter { file ->
            file !== program && if (folder.isEmpty()) '/' !in file.path else file.path.startsWith("$folder/")
        }.map { file -> file.path.removePrefix("$folder/") to file }
    }

    /** The content each key was first scanned with stays its catalog record through later edits. */
    private fun inspectSource(source: Source, scan: LibraryScanPipeline.ScanContext<DiskImageEntry>): List<DiskImageEntry> =
        inspectContent(source, scan).map { it.copy(catalogId = scan.earlier[it.id]?.catalogId ?: it.contentId) }

    /** Whether the library has scanned [source] before: an archive by its path, any other file by its key. */
    private fun known(source: Source, scan: LibraryScanPipeline.ScanContext<DiskImageEntry>): Boolean =
        if (format.isArchive(source.path)) scan.priorFrom(source.path).isNotEmpty()
        else scan.prior[keyOf(source.path, null).path] != null

    /**
     * A known image keeps the content ID it was scanned with, whatever its size and time now: a file
     * does not turn into another game, and rehashing every file is the player's Recheck all files.
     * An image that failed is read again once its file changes. A changed known file is logged, so
     * a scan that finds many can be explained.
     */
    private fun reuse(old: DiskImageEntry?, candidate: DiskImageEntry,
                      scan: LibraryScanPipeline.ScanContext<DiskImageEntry>): DiskImageEntry? {
        if (scan.forceHash || old == null) return null
        val unchanged = sameFingerprint(old, candidate)
        if (!unchanged) changedKnownFile(old, candidate)
        return when {
            old.error == null && old.contentId?.let(format::validId) == true ->
                candidate.copy(contentId = old.contentId)
            old.error != null && unchanged -> candidate.copy(error = old.error)
            else -> null
        }
    }

    private fun inspectContent(source: Source, scan: LibraryScanPipeline.ScanContext<DiskImageEntry>): List<DiskImageEntry> {
        if (format.isArchive(source.path)) {
            val oldEntries = scan.priorFrom(source.path)
            val formatsCurrent = (scan.previous?.root?.optInt("mediaFormatsVersion", 1) ?: 0) >= cache.mediaFormatsVersion
            // A known archive keeps its images; new formats read it again for images it may now hold.
            if (!scan.forceHash && formatsCurrent && oldEntries.isNotEmpty() &&
                oldEntries.all { it.error == null || sameSource(it, source) }) {
                if (oldEntries.any { !sameSource(it, source) }) changedKnownFile(oldEntries.first(), entry(source, null, -1, -1, null))
                return oldEntries.map { it.copy(uri = source.uri.toString(), sourceSize = source.size, sourceModified = source.newest) }
            }
            return openArchive(source).use { archive ->
                val images = archive.images(scan::checkCancelled)
                if (images.isEmpty()) return listOf(entry(source, null, source.size, -1, null).copy(holdsGame = false))
                images.map { image ->
                    scan.checkCancelled()
                    val data = image.data
                    val candidate = entry(source, image.name, data?.size ?: -1, data?.crc ?: -1, null,
                        companions = image.companions, folder = image.folderFiles)
                    try {
                        requireNotNull(data) { image.missing() }
                        reuse(scan.prior[candidate.id], candidate, scan) ?: run {
                            // The member's name in the archive's folder: the archive's own name
                            // mostly repeats it, and a shorter status keeps its count in view.
                            val folder = source.path.substringBeforeLast('/', "")
                            val member = image.name.substringAfterLast('/')
                            scan.hashStarted(if (folder.isEmpty()) member else "$folder/$member")
                            candidate.copy(contentId = archive.open(data).use {
                                contentId(it, data.size, data.crc, scan.cancelled, image.name, onRead = scan::hashRead)
                            })
                        }
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (error: Exception) {
                        scan.checkCancelled()
                        candidate.copy(error = error.message ?: context.getString(R.string.kairo_disk_image_unreadable))
                    }
                }
            }
        }
        if (format.isDescriptor(source.path)) {
            val data = source.data ?: error(context.getString(R.string.kairo_disk_track_data_missing, source.path.substringAfterLast('/')))
            val candidate = entry(source, null, data.size, -1, null, companions = listOf(data.uri.toString()))
            reuse(scan.prior[candidate.id], candidate, scan)?.let { return listOf(it) }
            scan.hashStarted(data.path)
            val id = context.contentResolver.openInputStream(data.uri)?.use {
                contentId(it, data.size, -1, scan.cancelled, source.path, onRead = scan::hashRead)
            } ?: error("Unable to open ${data.path}")
            return listOf(candidate.copy(contentId = id))
        }
        val candidate = entry(source, null, source.size, -1, null)
        reuse(scan.prior[candidate.id], candidate, scan)?.let { return listOf(it) }
        scan.hashStarted(source.path)
        val id = context.contentResolver.openInputStream(source.uri)?.use {
            contentId(it, source.size, -1, scan.cancelled, source.path, onRead = scan::hashRead)
        } ?: error("Unable to open disk image")
        return listOf(candidate.copy(contentId = id))
    }

    /**
     * A known file whose size or time differs from its record. Rare by nature; logged with its
     * values so a scan that finds many (Kairo#46: every archive after an update) shows why.
     */
    private fun changedKnownFile(old: DiskImageEntry, now: DiskImageEntry) {
        android.util.Log.w("KairoLibrary", "Known file changed: ${now.path}${now.zipEntry?.let { "/$it" } ?: ""}: " +
            "size ${old.sourceSize} -> ${now.sourceSize}, time ${old.sourceModified} -> ${now.sourceModified}, " +
            "image ${old.imageSize}/${old.imageCrc} -> ${now.imageSize}/${now.imageCrc}")
    }

    private fun failedSource(source: Source, scan: LibraryScanPipeline.ScanContext<DiskImageEntry>,
                             error: Exception): List<DiskImageEntry> = listOf(
        entry(source, null, source.size, -1, null,
            error.message ?: context.getString(if (format.isArchive(source.path)) R.string.kairo_disk_archive_unreadable
                else R.string.kairo_disk_image_unreadable),
            catalogId = scan.earlier[source.path]?.catalogId))

    /**
     * Returns the app-private working image for this entry, copying it from its source the first
     * time. The copy is verified against the entry's content ID. A descriptor's track data is copied
     * beside it, under the name the core derives from the descriptor's.
     */
    @Synchronized override fun prepare(entry: DiskImageEntry, cancelled: AtomicBoolean, progress: (String) -> Unit): File {
        require(entry.playable) { "This entry is not ready to launch" }
        if (format.isProgram(entry.displayName)) return prepareProgram(entry, cancelled, progress)
        // The working copy holds the game's in-disk saves, so it stays when the source is edited.
        val file = workingImage(entry)
        val descriptor = format.isDescriptor(entry.displayName)
        // The track data keeps its extension beside the descriptor, where the core looks for it.
        val data = if (descriptor) File(file.parentFile,
            "${file.name.substringBeforeLast('.')}.${format.extension(entry.companions.single())}") else file
        if (file.isFile && file.length() > 0 && data.isFile && data.length() > 0) return file
        if (entry.imageSize > 0) require(workingDirectory.usableSpace > entry.imageSize + 16L * 1024 * 1024) {
            context.getString(R.string.kairo_disk_no_space_image)
        }
        check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs()) { "Unable to create the working copy's folder" }
        val partialData = File(file.parentFile, "${data.name}.part")
        val partialDescriptor = File(file.parentFile, "${file.name}.part")
        partialData.delete()
        partialDescriptor.delete()
        try {
            progress(context.getString(R.string.kairo_disk_preparing, entry.displayName))
            if (entry.zipEntry == null) {
                val dataUri = if (descriptor) Uri.parse(entry.companions.single()) else Uri.parse(entry.uri)
                context.contentResolver.openInputStream(dataUri)?.use { input ->
                    partialData.outputStream().use { output -> copyVerified(entry, input, -1, cancelled, output) }
                } ?: error("Unable to open disk image")
                if (descriptor) context.contentResolver.openInputStream(Uri.parse(entry.uri))?.use { input ->
                    partialDescriptor.outputStream().use { input.copyTo(it) }
                } ?: error("Unable to open ${entry.displayName}")
            } else {
                openArchive(sourceOf(entry)).use { archive ->
                    val image = archive.images { checkCancelled(cancelled) }.firstOrNull { it.name == entry.zipEntry }
                        ?: error(context.getString(R.string.kairo_disk_member_missing, entry.zipEntry))
                    val member = image.data ?: error(image.missing())
                    archive.open(member).use { input ->
                        partialData.outputStream().use { output -> copyVerified(entry, input, member.crc, cancelled, output) }
                    }
                    if (descriptor) archive.open(image.entry).use { input ->
                        partialDescriptor.outputStream().use { input.copyTo(it) }
                    }
                }
            }
            DurableFiles.sync(partialData)
            if (descriptor) DurableFiles.sync(partialDescriptor)
            require(partialData.renameTo(data)) { "Unable to save working disk image" }
            if (descriptor) require(partialDescriptor.renameTo(file)) { "Unable to save ${entry.displayName}" }
            return file
        } catch (error: Exception) {
            partialData.delete()
            partialDescriptor.delete()
            throw error
        }
    }

    /**
     * The working copy of a program's folder, made the first time: the program, verified against
     * its content ID, and every file of its folder at its place in it. Returns the program's copy.
     */
    private fun prepareProgram(entry: DiskImageEntry, cancelled: AtomicBoolean, progress: (String) -> Unit): File {
        val folder = programFolder(entry)
        val program = File(folder, entry.displayName)
        // The working copy holds what the game saves in its folder, so it stays when the source is edited.
        if (program.isFile && program.length() > 0) return program
        require(workingDirectory.usableSpace > maxOf(entry.sourceSize, entry.imageSize) + 16L * 1024 * 1024) {
            context.getString(R.string.kairo_disk_no_space_folder)
        }
        check(folder.parentFile!!.isDirectory || folder.parentFile!!.mkdirs()) { "Unable to create the working copy's folder" }
        val partial = File(folder.parentFile, "${folder.name}.part")
        partial.deleteRecursively()
        fun target(name: String): File {
            val file = File(partial, name)
            require(file.canonicalPath.startsWith(partial.canonicalPath + File.separator)) { "Unsafe file name $name" }
            file.parentFile!!.mkdirs()
            return file
        }
        try {
            progress(context.getString(R.string.kairo_disk_preparing, entry.displayName))
            if (entry.zipEntry == null) {
                context.contentResolver.openInputStream(Uri.parse(entry.uri))?.use { input ->
                    target(entry.displayName).outputStream().use { output -> copyVerified(entry, input, -1, cancelled, output) }
                } ?: error("Unable to open ${entry.displayName}")
                for (file in entry.folder) {
                    checkCancelled(cancelled)
                    context.contentResolver.openInputStream(Uri.parse(file.source))?.use { input ->
                        target(file.name).outputStream().use { input.copyTo(it) }
                    } ?: error("Unable to open ${file.name}")
                }
            } else {
                openArchive(sourceOf(entry)).use { archive ->
                    val image = archive.images { checkCancelled(cancelled) }.firstOrNull { it.name == entry.zipEntry }
                        ?: error(context.getString(R.string.kairo_disk_member_missing, entry.zipEntry))
                    val member = image.data ?: error(image.missing())
                    archive.open(member).use { input ->
                        target(entry.displayName).outputStream().use { output -> copyVerified(entry, input, member.crc, cancelled, output) }
                    }
                    for ((file, folderFile) in image.folder.zip(image.folderFiles)) {
                        checkCancelled(cancelled)
                        archive.open(file).use { input -> target(folderFile.name).outputStream().use { input.copyTo(it) } }
                    }
                }
            }
            DurableFiles.sync(partial)
            check(!folder.exists() || folder.deleteRecursively()) { "Could not replace the working copy of ${entry.displayName}" }
            require(partial.renameTo(folder)) { "Unable to save the working copy of ${entry.displayName}" }
            return program
        } catch (error: Exception) {
            partial.deleteRecursively()
            throw error
        }
    }

    private fun copyVerified(entry: DiskImageEntry, input: InputStream, crc: Long, cancelled: AtomicBoolean,
                             output: OutputStream) {
        require(contentId(input, entry.imageSize, crc, cancelled, entry.displayName, output) == entry.contentId) {
            context.getString(R.string.kairo_disk_image_changed)
        }
    }

    /** Every file in an archive, by its path in it, and a way to read one; valid only inside [readArchive]. */
    interface ArchiveFiles {
        val files: Map<String, Long>
        fun open(name: String): InputStream
    }

    /** Reads the archive that holds [entry] in place, through [read]. */
    fun <T> readArchive(entry: DiskImageEntry, read: (ArchiveFiles) -> T): T =
        openArchive(sourceOf(entry)).use { archive ->
            read(object : ArchiveFiles {
                override val files = archive.allFiles()
                override fun open(name: String) = archive.openFile(name)
            })
        }

    private fun sourceOf(entry: DiskImageEntry): Source {
        require(entry.zipEntry != null) { "${entry.displayName} is not in an archive" }
        return Source(Uri.parse(entry.uri), entry.path, entry.sourceSize, entry.sourceModified)
    }

    /** A document to inspect; a descriptor also carries its track data, and a program its folder's files. */
    private data class Source(val uri: Uri, val path: String, val size: Long, val modified: Long,
                              val data: Source? = null, val folder: List<Pair<String, Source>> = emptyList()) {
        /** The newest of its documents' modification times, so an edit to any of them rehashes. */
        val newest: Long get() = maxOf(modified, data?.modified ?: 0, folder.maxOfOrNull { it.second.modified } ?: 0)
    }

    /** An archive member: its normalized path, size, and CRC-32 when the archive records one. */
    private class Member(val name: String, val size: Long, val crc: Long)

    /**
     * An image in an archive, with the member its bytes come from: itself, or a descriptor's track
     * data. A program also has its folder: the other members in its folder and below it.
     */
    private inner class Image(val entry: Member, val data: Member?, val folder: List<Member> = emptyList()) {
        val name: String get() = entry.name
        val companions: List<String> get() = if (data == null || data === entry) emptyList() else listOf(data.name)
        val folderFiles: List<FolderFile> get() {
            val directory = name.substringBeforeLast('/', "")
            return folder.map { FolderFile(if (directory.isEmpty()) it.name else it.name.removePrefix("$directory/"), it.name) }
        }
        fun missing() = context.getString(R.string.kairo_disk_track_data_missing_archive, name)
    }

    /** A ZIP or 7z archive's members, read in place from its document. */
    private abstract inner class Archive(private val document: ParcelFileDescriptor) : Closeable {
        protected abstract fun members(checkCancelled: () -> Unit): List<Member>
        abstract fun open(member: Member): InputStream
        /** Every file, of any kind, by its path in the archive, with its size. */
        abstract fun allFiles(): Map<String, Long>
        abstract fun openFile(name: String): InputStream
        protected abstract fun closeReader()

        override fun close() {
            try { closeReader() } finally { document.close() }
        }

        /**
         * The archive's images, none for an archive of other files; a descriptor whose track data
         * is missing has none, and fails alone.
         */
        fun images(checkCancelled: () -> Unit): List<Image> {
            val members = members(checkCancelled)
            val byName = members.associateBy { it.name.lowercase(Locale.ROOT) }
            val images = members.filter { format.isImage(it.name) && !format.superseded(it.name, byName.keys) }
            require(images.size <= MAX_ARCHIVE_IMAGES) { context.getString(R.string.kairo_disk_archive_too_many_images) }
            return images.map { member ->
                val data = if (format.isDescriptor(member.name)) format.dataOf(member.name, byName) else member
                if (data != null) require(data.size in 1..format.limit(member.name)) { context.getString(R.string.kairo_disk_image_too_large) }
                if (format.isProgram(member.name)) {
                    val directory = member.name.substringBeforeLast('/', "")
                    Image(member, data, members.filter { other ->
                        other !== member && (directory.isEmpty() || other.name.startsWith("$directory/"))
                    })
                } else Image(member, data)
            }
        }
    }

    /**
     * Opens [source]'s archive where it is: the document's file descriptor is seekable, so the
     * readers fetch only the bytes they need and nothing is copied.
     */
    private fun openArchive(source: Source): Archive {
        require(source.size <= MAX_ARCHIVE_BYTES) { context.getString(R.string.kairo_disk_archive_too_large) }
        val document = context.contentResolver.openFileDescriptor(source.uri, "r") ?: error("Unable to open archive")
        try {
            if (source.size >= 0) require(document.statSize == source.size) {
                context.getString(R.string.kairo_disk_archive_changed)
            }
            val channel = FileInputStream(document.fileDescriptor).channel
            // A provider that streams the document through a pipe cannot be read in place.
            require(document.statSize >= 0 && runCatching { channel.position(0) }.isSuccess) {
                context.getString(R.string.kairo_disk_archive_not_seekable)
            }
            return if (format.extension(source.path) == "7z") SevenZArchive(document, channel) else ZipArchive(document, channel)
        } catch (error: Exception) {
            document.close()
            throw error
        }
    }

    private inner class ZipArchive(document: ParcelFileDescriptor, channel: java.nio.channels.FileChannel) : Archive(document) {
        private val zip = ZipFile.builder().setSeekableByteChannel(channel).get()
        private val byName = HashMap<String, ZipArchiveEntry>()

        override fun members(checkCancelled: () -> Unit): List<Member> {
            val names = HashSet<String>()
            val members = ArrayList<Member>()
            var inspected = 0
            for (item in zip.entries.asSequence()) {
                checkCancelled()
                require(++inspected <= MAX_ARCHIVE_ENTRIES) { context.getString(R.string.kairo_disk_archive_too_many_entries) }
                val name = safeEntryName(item.name)
                require(names.add(name.lowercase(Locale.ROOT))) { context.getString(R.string.kairo_disk_archive_duplicate) }
                if (item.isDirectory || !(format.listsEveryFile || format.isImage(name) || format.isData(name))) continue
                // An empty file (some program folders have them) expands to nothing.
                require(item.size == 0L || (item.compressedSize > 0 && item.size / item.compressedSize <= MAX_EXPANSION_RATIO)) {
                    context.getString(R.string.kairo_disk_image_expansion)
                }
                byName[item.name] = item
                members.add(Member(item.name, item.size, item.crc))
            }
            return members
        }

        override fun open(member: Member): InputStream =
            zip.getInputStream(byName[member.name] ?: error(context.getString(R.string.kairo_disk_member_missing, member.name)))

        override fun allFiles() = zip.entries.asSequence().filter { !it.isDirectory }.associate { it.name to it.size }

        override fun openFile(name: String): InputStream =
            zip.getInputStream(zip.getEntry(name) ?: error(context.getString(R.string.kairo_disk_member_missing, name)))

        override fun closeReader() = zip.close()
    }

    private inner class SevenZArchive(document: ParcelFileDescriptor, channel: java.nio.channels.FileChannel) : Archive(document) {
        private val archive = SevenZFile.builder().setSeekableByteChannel(channel).get()
        private val byName = HashMap<String, org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry>()

        override fun members(checkCancelled: () -> Unit): List<Member> {
            val names = HashSet<String>()
            val members = ArrayList<Member>()
            var inspected = 0
            for (item in archive.entries) {
                checkCancelled()
                require(++inspected <= MAX_ARCHIVE_ENTRIES) { context.getString(R.string.kairo_disk_archive_too_many_entries) }
                val name = safeEntryName(item.name)
                require(names.add(name.lowercase(Locale.ROOT))) { context.getString(R.string.kairo_disk_archive_duplicate) }
                if (item.isDirectory || !item.hasStream() ||
                    !(format.listsEveryFile || format.isImage(name) || format.isData(name))) continue
                byName[item.name] = item
                members.add(Member(item.name, item.size, if (item.hasCrc) item.crcValue else -1))
            }
            return members
        }

        override fun open(member: Member): InputStream =
            archive.getInputStream(byName[member.name] ?: error(context.getString(R.string.kairo_disk_member_missing, member.name)))

        override fun allFiles() = archive.entries.filter { !it.isDirectory && it.hasStream() }.associate { it.name to it.size }

        override fun openFile(name: String): InputStream =
            archive.getInputStream(archive.entries.firstOrNull { it.name == name } ?: error(context.getString(R.string.kairo_disk_member_missing, name)))

        override fun closeReader() = archive.close()
    }

    /** The content ID of [name]'s bytes from [input]: its track data for a descriptor. */
    private fun contentId(input: InputStream, expectedSize: Long, expectedCrc: Long, cancelled: AtomicBoolean,
                          name: String, output: OutputStream? = null, onRead: (Long, Long) -> Unit = { _, _ -> }): String {
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
            require(count <= limit) { context.getString(R.string.kairo_disk_image_too_large) }
            digest.update(buffer, 0, read)
            crc.update(buffer, 0, read)
            output?.write(buffer, 0, read)
            onRead(count, expectedSize)
        }
        require(count > 0) { context.getString(R.string.kairo_disk_image_empty) }
        if (expectedSize >= 0) require(count == expectedSize) { context.getString(R.string.kairo_disk_image_size_changed) }
        if (expectedCrc >= 0) require(crc.value == expectedCrc) { context.getString(R.string.kairo_disk_archive_checksum) }
        return "sha256-${format.kind(name)}-v1:${digest.digest().toHex()}"
    }

    /**
     * A loose descriptor's or program's modification time is its newest file's, so changed track
     * data or program files rehash. A loose program's folder comes from [source]; one in an archive
     * passes [folder].
     */
    private fun entry(source: Source, member: String?, imageSize: Long, imageCrc: Long, contentId: String?,
                      error: String? = null, companions: List<String> = emptyList(),
                      key: GameKey = keyOf(source.path, member), catalogId: String? = contentId,
                      folder: List<FolderFile> = source.folder.map { (name, file) -> FolderFile(name, file.uri.toString()) }
    ): DiskImageEntry =
        DiskImageEntry(key.path, source.uri.toString(), source.path, member, source.size,
            source.newest, imageSize, imageCrc, contentId, error, companions,
            catalogId, label(member ?: source.path), folder = folder)

    /** An image's label; a folder or archive that failed whole is labeled as an image of none. */
    private fun label(name: String): String =
        format.kind(name)?.let { format.labels.getValue(it).resolve(context) } ?: context.getString(R.string.kairo_disk_image)

    /** [entry] with the label its formats give it, as for one an earlier cache layout read. */
    private fun labeled(entry: DiskImageEntry) = entry.copy(mediaText = label(entry.zipEntry ?: entry.path))

    /** An image's key: its path in the games folder, and its path inside an archive. */
    fun keyOf(path: String, member: String?) = member?.let { GameKey.inArchive(path, it) } ?: GameKey(path)

    private fun trusted(source: Source) = source.size >= 0 && source.modified > 0
    private fun sameSource(old: DiskImageEntry, source: Source) =
        old.sourceSize == source.size && old.sourceModified == source.newest
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
        writeInt(entry.folder.size); entry.folder.forEach { writeText(it.name); writeText(it.source) }
    }

    private fun readCacheRecord(input: DataInputStream) = with(input) {
        readCacheRecordV5(input).copy(folder = List(readInt()) { FolderFile(readText(), readText()) })
    }

    /** Layout 5, from before programs' folders were recorded. */
    private fun readCacheRecordV5(input: DataInputStream) = readCacheRecordV4(input).copy(holdsGame = input.readBoolean())

    /** Layout 4, from before archives without a game were remembered. */
    private fun readCacheRecordV4(input: DataInputStream) = with(input) {
        labeled(DiskImageEntry(readText(), readText(), readText(), readOptionalText(), readLong(), readLong(),
            readLong(), readLong(), readOptionalText(), readOptionalText(), List(readInt()) { readText() },
            readOptionalText()))
    }

    companion object {
        /** The cache record layout: 5 records whether an archive holds a game, 6 a program's folder. */
        private const val CACHE_LAYOUT = 6
        private const val MAX_DOCUMENTS = 50_000
        private const val MAX_ARCHIVE_ENTRIES = 50_000
        private const val MAX_ARCHIVE_IMAGES = 1_000
        private const val MAX_ARCHIVE_BYTES = 8L * 1024 * 1024 * 1024
        private const val MAX_EXPANSION_RATIO = 1000L
        private const val MAX_STORE_BYTES = 16 * 1024 * 1024
        /** A working copy's kind in a [GamePathMigration.Conflict]. */
        private const val WORKING_COPY = "disk"
        private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes).toHex()
        private fun checkCancelled(cancelled: AtomicBoolean) {
            if (cancelled.get()) throw CancellationException("Cancelled")
        }
    }
}
