package com.mrjackspade.kairo.frontend

import android.app.Instrumentation
import android.content.Intent
import android.os.Debug
import android.os.SystemClock
import android.widget.EditText
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Comparable cold/warm editing measurements against each host's installed library. */
object LibrarySearchFixture {
    fun measure(test: Instrumentation, trace: Boolean): String {
        val launch = test.targetContext.packageManager.getLaunchIntentForPackage(test.targetContext.packageName)!!
        val activity = test.startActivitySync(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        fun ui(action: () -> Unit) {
            var failure: Throwable? = null
            test.runOnMainSync { try { action() } catch (caught: Throwable) { failure = caught } }
            test.waitForIdleSync()
            failure?.let { throw it }
        }
        fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name)
            .apply { isAccessible = true }.get(owner)
        val out = StringBuilder()
        var tracing = false
        try {
            Thread.sleep(8000)
            lateinit var search: EditText
            lateinit var screen: LibraryScreen<*>
            ui {
                val flow = field(activity, "libraryFlow")!!
                screen = flow.javaClass.getMethod("getScreen").invoke(flow) as LibraryScreen<*>
                search = field(screen, "search") as EditText
                out.append("librarySize=${(field(screen, "allEntries") as List<*>).size}\n")
                search.visibility = android.view.View.VISIBLE
                (field(screen, "searchRow") as android.view.View).visibility = android.view.View.VISIBLE
            }
            val catalog = field(screen, "catalog") as LibraryCatalog
            @Suppress("UNCHECKED_CAST")
            val all = field(screen, "allEntries") as List<LibraryItem>
            val shards = all.mapNotNull { it.contentId?.substringAfterLast(':')?.take(2) }.distinct().size
            out.append("catalogShardPrefixes=$shards\n")
            if (!trace) {
                check(shards > 8) { "Need a real library spanning more than eight shards" }
                // Stop the previous snapshot before clearing the actual host caches.
                ui { field(screen, "searchIndex")!!.javaClass.getMethod("close").invoke(field(screen, "searchIndex")) }
                synchronized(catalog) {
                    catalog.javaClass.declaredFields.filter { android.util.LruCache::class.java.isAssignableFrom(it.type) }
                        .forEach { cache ->
                            (cache.apply { isAccessible = true }.get(catalog) as android.util.LruCache<*, *>).evictAll()
                        }
                }
                ui { screen.refreshArtwork() }
            }
            if (trace) {
                Debug.startMethodTracingSampling(File(test.targetContext.filesDir, "search-profile.trace").path,
                    32 * 1024 * 1024, 1000)
                tracing = true
            }
            fun awaitIndex() {
                val indexWait = SystemClock.elapsedRealtime()
                var ready = false
                var count = 0
                while (!ready && SystemClock.elapsedRealtime() - indexWait < 15000) {
                    ui {
                        @Suppress("UNCHECKED_CAST")
                        val records = field(screen, "rowMetadata") as Map<String, LibraryGame>
                        count = records.size
                        ready = all.all { it.id in records }
                    }
                    if (!ready) Thread.sleep(20)
                }
                check(ready) { "Search index incomplete: $count/${all.size}, attached=${screen.isAttachedToWindow}\n$out" }
                out.append("indexWaitMs=${SystemClock.elapsedRealtime() - indexWait}\n")
            }
            for (phase in if (trace) listOf("trace") else listOf("cold", "warm")) {
                if (phase == "warm") awaitIndex()
                for (query in listOf("a", "al", "ali", "alic", "alice", "alic", "ali", "al", "a", "")) {
                    val drawn = CountDownLatch(1)
                    var editMs = 0L
                    var frameMs = 0L
                    ui {
                        val start = SystemClock.elapsedRealtimeNanos()
                        val listener = object : android.view.ViewTreeObserver.OnDrawListener {
                            override fun onDraw() {
                                frameMs = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000
                                search.post { search.viewTreeObserver.removeOnDrawListener(this) }
                                drawn.countDown()
                            }
                        }
                        search.viewTreeObserver.addOnDrawListener(listener)
                        search.setText(query)
                        editMs = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000
                    }
                    check(drawn.await(30, TimeUnit.SECONDS)) { "Search did not draw" }
                    out.append("phase=$phase query='$query' editMs=$editMs drawMs=$frameMs\n")
                    if (!trace) check(editMs < 500 && frameMs < 1000) { "Search input stalled: $out" }
                }
            }
            if (!trace) {
                awaitIndex()
                for (query in listOf("a", "unmatched_987654321", "ALICE")) {
                    var expected = emptyList<String>()
                    ui {
                        @Suppress("UNCHECKED_CAST")
                        val metadata = field(screen, "rowMetadata") as Map<String, LibraryGame>
                        check(all.all { it.id in metadata }) { "Search index did not finish" }
                        expected = all.filter { metadata.getValue(it.id).title.contains(query, true) ||
                            it.displayName.contains(query, true) }.map { it.id }
                    }
                    val start = SystemClock.elapsedRealtime()
                    ui { search.setText(query) }
                    var matches = false
                    while (!matches && SystemClock.elapsedRealtime() - start < 5000) {
                        ui {
                            @Suppress("UNCHECKED_CAST")
                            val shown = field(screen, "entries") as List<LibraryItem>
                            matches = shown.map { it.id } == expected
                        }
                        if (!matches) Thread.sleep(10)
                    }
                    check(matches) { "Incorrect results for $query" }
                    out.append("results='$query' count=${expected.size} settledMs=${SystemClock.elapsedRealtime() - start}\n")
                }
            }
            if (!trace) {
                val entry = all.first { it.contentId != null }
                val id = entry.contentId!!
                val overrides = field(catalog, "overrides") as GameMetadataOverrides
                val file = field(catalog, "overridesFile") as File
                val originalBytes = file.takeIf { it.exists() }?.readBytes()
                val original = overrides.record(id)
                val originalTitle = original?.optString("title")?.takeIf { original.has("title") }
                val marker = "Kairo search fixture 50 unique title"
                check(all.none { it.displayName.contains(marker, true) })
                fun awaitRows(expected: Set<String>) {
                    val deadline = SystemClock.elapsedRealtime() + 15000
                    var matched = false
                    while (!matched && SystemClock.elapsedRealtime() < deadline) {
                        ui {
                            val rows = field(screen, "entries") as List<*>
                            matched = rows.map { (it as LibraryItem).id }.toSet() == expected
                        }
                        if (!matched) Thread.sleep(10)
                    }
                    check(matched) { "Metadata refresh left stale search results" }
                }
                try {
                    overrides.set(id, "title", marker)
                    ui { screen.refreshArtwork(); search.setText(marker) }
                    awaitRows(all.filter { it.contentId == id }.map { it.id }.toSet())
                    overrides.set(id, "title", originalTitle)
                    @Suppress("UNCHECKED_CAST")
                    ui { (screen as LibraryScreen<LibraryItem>).showEntries(all) }
                    awaitRows(emptySet())
                    check(overrides.record(id)?.toString() == original?.toString())
                    out.append("Live title override and catalog snapshot refresh invalidate an active query: OK\n")
                } finally {
                    overrides.set(id, "title", originalTitle)
                    if (originalBytes == null) check(!file.exists() || file.delete())
                    else file.writeBytes(originalBytes)
                }
            }

        } finally {
            if (tracing) Debug.stopMethodTracing()
            ui { activity.finish() }
        }
        if (!trace) {
            SearchIndexFixture.verify(test)
            out.append("Latest query, snapshot replacement, clear, close, case/title matching: OK\n")
        }
        return out.toString()
    }
}
