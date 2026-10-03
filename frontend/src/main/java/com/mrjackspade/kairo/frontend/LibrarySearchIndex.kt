package com.mrjackspade.kairo.frontend

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** One worker owns catalog indexing; UI callbacks only publish the current snapshot/query. */
internal class LibrarySearchIndex<T : LibraryItem>(
    private val catalog: LibraryCatalog,
    private val post: (() -> Unit) -> Unit,
    private val indexed: (Map<String, LibraryGame>) -> Unit
) {
    private data class Row<T>(val entry: T, val title: String, val fileName: String)
    private class Snapshot<T>(val entries: List<T>, val metadata: MutableMap<String, LibraryGame>) {
        @Volatile var cancelled = false
        var rows: List<Row<T>>? = null // Worker-owned, never mutated after publication.
    }
    private var snapshot: Snapshot<T>? = null
    private var request = 0L
    private var executor: ThreadPoolExecutor? = null

    fun replace(entries: List<T>, seed: Map<String, LibraryGame>) {
        snapshot?.cancelled = true
        executor?.queue?.clear()
        snapshot = Snapshot(entries.toList(), HashMap(seed))
        request++
    }

    fun cancelQuery() { request++ }

    private fun worker() = executor ?: ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
        LinkedBlockingQueue<Runnable>()).also { executor = it }

    private fun build(source: Snapshot<T>): List<Row<T>>? {
        source.rows?.let { return it }
        val rows = ArrayList<Row<T>>(source.entries.size)
        val pending = HashMap<String, LibraryGame>()
        var publishedAt = android.os.SystemClock.uptimeMillis()
        fun publishPending() {
            if (pending.isEmpty()) return
            val records = pending.toMap()
            pending.clear()
            post { if (snapshot === source && !source.cancelled) indexed(records) }
            publishedAt = android.os.SystemClock.uptimeMillis()
        }
        for (entry in source.entries) {
            if (source.cancelled || Thread.currentThread().isInterrupted) return null
            val name = entry.displayName
            val game = source.metadata.getOrPut(entry.id) { catalog.resolve(entry.contentId ?: "", name) }
            rows.add(Row(entry, game.title, name))
            pending[entry.id] = game
            if (android.os.SystemClock.uptimeMillis() - publishedAt >= 100) publishPending()
        }
        if (source.cancelled) return null
        source.rows = rows
        publishPending()
        return rows
    }

    /** Called after the initial library frame so cold indexing does not compete with its rows. */
    fun warm() {
        val source = snapshot ?: return
        worker().execute { build(source) }
    }

    fun search(query: String, publish: (List<T>) -> Unit) {
        val source = snapshot ?: return
        val version = ++request
        val worker = worker()
        // Keep the running index build, but discard obsolete queued queries.
        worker.queue.clear()
        worker.execute {
            val rows = build(source) ?: return@execute
            val matches = rows.filter { it.title.contains(query, true) || it.fileName.contains(query, true) }
                .map { it.entry }
            post {
                if (snapshot === source && !source.cancelled && version == request) publish(matches)
            }
        }
    }

    fun close() {
        request++
        snapshot?.cancelled = true
        snapshot = null
        executor?.shutdownNow()
        executor = null
    }
}
