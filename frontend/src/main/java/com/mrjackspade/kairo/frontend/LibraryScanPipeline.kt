package com.mrjackspade.kairo.frontend

import android.net.Uri
import org.json.JSONObject
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Coordinates a library scan while the emulator supplies media recognition and hashing. */
class LibraryScanPipeline<Work : Any, Entry : Any>(
    private val cache: VersionedLibraryCache<Entry>,
    private val entryId: (Entry) -> String,
    private val enumerate: (Uri, AtomicBoolean, (String) -> Unit) -> Plan<Work, Entry>,
    private val inspect: (Work, ScanContext<Entry>) -> List<Entry>,
    private val failed: (Work, ScanContext<Entry>, Exception) -> List<Entry>,
    private val label: (Work, Int, Int) -> String? = { _, _, _ -> null },
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
        val forceHash: Boolean,
        val cancelled: AtomicBoolean,
        val progress: (String) -> Unit,
        private val countHash: () -> Unit
    ) {
        fun checkCancelled() {
            if (cancelled.get()) throw CancellationException("Cancelled")
        }

        fun hashStarted() = countHash()
    }

    private val hashes = AtomicInteger()
    val hashCount: Int get() = hashes.get()

    @Synchronized fun scan(tree: Uri, forceHash: Boolean, cancelled: AtomicBoolean,
                           progress: (String) -> Unit): List<Entry> {
        if (cancelled.get()) throw CancellationException("Cancelled")
        val previous = cache.readForTree(tree.toString())
        val prior = if (forceHash) emptyMap() else
            previous?.entries?.associateBy(entryId).orEmpty()
        hashes.set(0)
        val progressLock = Any()
        val report: (String) -> Unit = { message -> synchronized(progressLock) {
            if (!cancelled.get()) progress(message)
        } }
        val context = ScanContext(previous, prior, forceHash, cancelled, report) { hashes.incrementAndGet(); Unit }
        val plan = enumerate(tree, cancelled, progress)
        context.checkCancelled()
        val entries = ArrayList<Entry>()
        entries.addAll(plan.initialEntries)
        val inspected = ParallelScanWork.map(plan.work, cancelled) { index, work ->
            context.checkCancelled()
            label(work, index, plan.work.size)?.let(report)
            try {
                inspect(work, context)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                context.checkCancelled()
                failed(work, context, error)
            }
        }
        inspected.forEach(entries::addAll)
        entries.addAll(plan.finalEntries)
        context.checkCancelled()
        val saved = cacheOrder(entries)
        cache.write(tree.toString(), saved, metadata())
        afterCommit(plan)
        return displayOrder(saved)
    }
}
