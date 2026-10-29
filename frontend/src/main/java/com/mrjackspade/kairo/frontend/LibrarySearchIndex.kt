package com.mrjackspade.kairo.frontend

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** One worker owns catalog indexing; UI callbacks only publish the current snapshot/query. */
internal class LibrarySearchIndex<T : LibraryItem>(
    private val catalog: LibraryCatalog,
    private val post: (() -> Unit) -> Unit,
    private val indexed: (Map<String, LibraryGame>) -> Unit
) {
    private data class Row<T>(val entry: T, val titles: List<String>, val fileName: String)
    private class Snapshot<T>(val entries: List<T>, val catalogGeneration: Int) {
        @Volatile var cancelled = false
        var rows: List<Row<T>>? = null // Worker-owned, never mutated after publication.
    }
    /** A catalog record stays valid for its entry while the entry's content and name are unchanged. */
    private class Resolved(val contentId: String?, val catalogId: String?, val name: String,
                           val generation: Int, val game: LibraryGame)
    private var snapshot: Snapshot<T>? = null
    private var request = 0L
    private var executor: ThreadPoolExecutor? = null
    // Resolving every catalog record takes far longer than a library edit; snapshots share them.
    private val resolved = ConcurrentHashMap<String, Resolved>()
    @Volatile private var catalogGeneration = 0

    fun replace(entries: List<T>) {
        snapshot?.cancelled = true
        executor?.queue?.clear()
        snapshot = Snapshot(entries.toList(), catalogGeneration)
        request++
    }

    /** These entries' catalog records changed, as after editing a game's title. */
    fun forget(ids: Collection<String>) { ids.forEach(resolved::remove) }

    /** The catalog or its artwork changed: resolve every record again. */
    fun forgetMetadata() {
        catalogGeneration++
        resolved.clear()
    }

    fun cancelQuery() { request++ }

    private fun worker() = executor ?: ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
        LinkedBlockingQueue<Runnable>()).also { executor = it }

    private fun build(source: Snapshot<T>): List<Row<T>>? {
        source.rows?.let { return it }
        val titles = arrayOfNulls<List<String>>(source.entries.size)
        val pending = HashMap<String, LibraryGame>()
        var publishedAt = android.os.SystemClock.uptimeMillis()
        fun publishPending() {
            if (pending.isEmpty()) return
            val records = pending.toMap()
            pending.clear()
            post { if (snapshot === source && !source.cancelled) indexed(records) }
            publishedAt = android.os.SystemClock.uptimeMillis()
        }
        // Catalogs shard records by content ID. Resolving in that order reads each shard once,
        // instead of once per game when the library's name order jumps between shards.
        val order = source.entries.indices.sortedBy { source.entries[it].contentId ?: "" }
        for (index in order) {
            val entry = source.entries[index]
            if (source.cancelled || Thread.currentThread().isInterrupted) return null
            val name = entry.displayName
            val known = resolved[entry.id]?.takeIf {
                it.contentId == entry.contentId && it.catalogId == entry.catalogId && it.name == name &&
                    it.generation == source.catalogGeneration
            }
            val game = known?.game ?: catalog.resolve(entry).also { game ->
                if (source.catalogGeneration == catalogGeneration)
                    resolved[entry.id] = Resolved(entry.contentId, entry.catalogId, name,
                        source.catalogGeneration, game)
            }
            titles[index] = game.searchTitles
            pending[entry.id] = game
            if (android.os.SystemClock.uptimeMillis() - publishedAt >= 100) publishPending()
        }
        if (source.cancelled) return null
        val rows = source.entries.mapIndexed { index, entry -> Row(entry, titles[index]!!, entry.displayName) }
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
            val matches = rows.filter { row -> row.titles.any { it.contains(query, true) } || row.fileName.contains(query, true) }
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
