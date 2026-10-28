package com.mrjackspade.kairo.frontend

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Coordinates a library scan while the emulator supplies media recognition and hashing. */
class LibraryScanPipeline<Work : Any, Entry : Any>(
    /** Puts the scan's status into the player's language. */
    private val context: Context,
    private val cache: VersionedLibraryCache<Entry>,
    private val entryId: (Entry) -> String,
    private val enumerate: (Uri, ScanDepth, AtomicBoolean, (String) -> Unit) -> Plan<Work, Entry>,
    private val inspect: (Work, ScanContext<Entry>) -> List<Entry>,
    private val failed: (Work, ScanContext<Entry>, Exception) -> List<Entry>,
    /** The file a work item reads, as its status names it. */
    private val describe: (Work) -> String,
    /**
     * Whether the library already knows a work item. A known item keeps the identity it was
     * scanned with and is not read again unless the player rechecks every file: a known file does
     * not turn into another game.
     */
    private val known: (Work, ScanContext<Entry>) -> Boolean,
    /** The source document an entry was read from, for [ScanContext.priorFrom]; one archive has several entries. */
    private val sourceOf: (Entry) -> String,
    private val cacheOrder: (List<Entry>) -> List<Entry> = { it },
    private val displayOrder: (List<Entry>) -> List<Entry> = { it },
    private val metadata: () -> JSONObject = { JSONObject() },
    private val afterCommit: (Plan<Work, Entry>) -> Unit = {}
) {
    data class Plan<Work : Any, Entry : Any>(
        val work: List<Work>,
        val initialEntries: List<Entry> = emptyList(),
        val finalEntries: List<Entry> = emptyList()
    )

    class ScanContext<Entry : Any> internal constructor(
        val previous: VersionedLibraryCache.Snapshot<Entry>?,
        val prior: Map<String, Entry>,
        /** The previous scan's entries by ID, even when [forceHash] ignores them for hashing. */
        val earlier: Map<String, Entry>,
        val forceHash: Boolean,
        val cancelled: AtomicBoolean,
        private val sourceOf: (Entry) -> String,
        private val countHash: () -> Unit
    ) {
        /** [prior]'s entries by the document they were read from, grouped once per scan. */
        private val bySource: Map<String, List<Entry>> by lazy { prior.values.groupBy(sourceOf) }
        internal var status: ScanProgress? = null

        /** The entries [prior] holds from the document [source]: an archive's images. */
        fun priorFrom(source: String): List<Entry> = bySource[source].orEmpty()

        fun checkCancelled() {
            if (cancelled.get()) throw CancellationException("Cancelled")
        }

        /** The worker hashes [detail]: the file, or a member of the archive it reads. */
        fun hashStarted(detail: String) {
            countHash()
            status?.hashing(detail)
        }

        /** [read] of [total] bytes of the current hash are done. */
        fun hashRead(read: Long, total: Long) {
            status?.read(read, total)
        }
    }

    private val hashes = AtomicInteger()
    val hashCount: Int get() = hashes.get()
    /** Scans and in-place edits write the same cache file. */
    private val writeLock = Any()

    /** The folder's cached library, in the order a scan returns it. */
    fun cached(tree: Uri): List<Entry> =
        cache.readForTree(tree.toString())?.entries?.let(displayOrder).orEmpty()

    /** [entries] in the order a scan returns them, as after adding an entry. */
    fun inScanOrder(entries: List<Entry>): List<Entry> = displayOrder(cacheOrder(entries))

    /** Saves a library the app changed without scanning, exactly as a scan saves it. */
    fun store(tree: Uri, entries: List<Entry>) {
        synchronized(writeLock) { cache.write(tree.toString(), cacheOrder(entries), metadata()) }
    }

    @Synchronized fun scan(tree: Uri, depth: ScanDepth, cancelled: AtomicBoolean,
                           progress: (String) -> Unit): List<Entry> {
        val forceHash = depth.rehash
        if (cancelled.get()) throw CancellationException("Cancelled")
        // Each phase's time goes to the log, so a scan can be profiled on a device (Kairo#32).
        val start = android.os.SystemClock.elapsedRealtime()
        val previous = try {
            cache.readForTree(tree.toString())
        } catch (failure: Exception) {
            // The cache only saves rehashing: an unreadable one is reported, then replaced by this scan.
            android.util.Log.e("KairoLibrary", "Library cache unreadable; rescanning without it", failure)
            progress(context.getString(R.string.kairo_scan_cache_unreadable))
            null
        }
        val earlier = previous?.entries?.associateBy(entryId).orEmpty()
        val prior = if (forceHash) emptyMap() else earlier
        hashes.set(0)
        // Workers report concurrently, so [progress] must be thread-safe (LibraryFlow keeps only the newest).
        val report: (UiText) -> Unit = { message -> if (!cancelled.get()) progress(context.text(message)) }
        val scan = ScanContext(previous, prior, earlier, forceHash, cancelled, sourceOf) { hashes.incrementAndGet(); Unit }
        val read = android.os.SystemClock.elapsedRealtime()
        val plan = enumerate(tree, depth, cancelled, progress)
        val walked = android.os.SystemClock.elapsedRealtime()
        scan.checkCancelled()
        val entries = ArrayList<Entry>()
        entries.addAll(plan.initialEntries)
        val hash = plan.work.map { forceHash || !known(it, scan) }
        val toHash = hash.count { it }
        scan.status = ScanProgress(toHash, hash.size - toHash, report)
        val inspected = ParallelScanWork.map(plan.work, cancelled) { index, work ->
            scan.checkCancelled()
            scan.status!!.start(describe(work), hash[index])
            try {
                inspect(work, scan)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                scan.checkCancelled()
                failed(work, scan, error)
            }
        }
        val checked = android.os.SystemClock.elapsedRealtime()
        inspected.forEach(entries::addAll)
        entries.addAll(plan.finalEntries)
        scan.checkCancelled()
        val saved = cacheOrder(entries)
        synchronized(writeLock) { cache.write(tree.toString(), saved, metadata()) }
        val written = android.os.SystemClock.elapsedRealtime()
        android.util.Log.i("KairoLibrary", "Scan: cache read ${read - start} ms, walk ${walked - read} ms " +
            "(${plan.work.size} items), check ${checked - walked} ms (${hashes.get()} hashed), " +
            "write ${written - checked} ms, ${saved.size} entries")
        afterCommit(plan)
        return displayOrder(saved)
    }
}
