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
            if (trace) {
                Debug.startMethodTracingSampling(File(test.targetContext.filesDir, "search-profile.trace").path,
                    32 * 1024 * 1024, 1000)
                tracing = true
            }
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
                out.append("query='$query' editMs=$editMs drawMs=$frameMs\n")
                Thread.sleep(100)
            }
            if (!trace) {
                @Suppress("UNCHECKED_CAST")
                val all = field(screen, "allEntries") as List<LibraryItem>
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
                out.append("indexWaitAfterTypingMs=${SystemClock.elapsedRealtime() - indexWait}\n")
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
