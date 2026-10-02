package com.mrjackspade.kairo.frontend

import android.app.Instrumentation
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Delayed work must not resurrect an old query, catalog snapshot, or detached screen. */
object SearchIndexFixture {
    fun verify(test: Instrumentation) {
        fun item(name: String) = object : LibraryItem {
            override val id = name
            override val contentId = name
            override val path = name
            override val displayName = name
            override val zipEntry: String? = null
            override val error: String? = null
            override val playable = true
            override val mediaLabel = "fixture"
        }
        fun game(name: String) = object : LibraryGame {
            override val title = "Catalog $name"
            override val description: String? = null
            override val boxArt: String? = null
            override val preview: String? = null
            override val tags = emptyList<String>()
        }
        val blocked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val resolved = AtomicInteger()
        val catalog = object : LibraryCatalog {
            override fun resolve(contentId: String, fileName: String): LibraryGame {
                check(Looper.myLooper() != Looper.getMainLooper()) { "Catalog searched on UI thread" }
                resolved.incrementAndGet()
                if (contentId == "slow") {
                    blocked.countDown()
                    check(release.await(10, TimeUnit.SECONDS))
                }
                return game(fileName)
            }
            override fun hiddenFromLibrary(contentId: String) = false
            override fun openArtwork(path: String): java.io.InputStream = error("No artwork")
        }
        val type = Class.forName("com.mrjackspade.kairo.frontend.LibrarySearchIndex")
        val handler = Handler(Looper.getMainLooper())
        val posted = ArrayList<String>()
        val index = type.constructors.single().newInstance(catalog,
            { action: () -> Unit -> handler.post(action); Unit }, { _: Map<String, LibraryGame> -> Unit })
        fun ui(action: () -> Unit) {
            var failure: Throwable? = null
            test.runOnMainSync { try { action() } catch (caught: Throwable) { failure = caught } }
            test.waitForIdleSync()
            failure?.let { throw it }
        }
        fun replace(names: List<String>) = type.getMethod("replace", List::class.java, Map::class.java)
            .invoke(index, names.map(::item), emptyMap<String, LibraryGame>())
        fun search(query: String, done: CountDownLatch? = null) {
            val callback: (List<LibraryItem>) -> Unit = { rows ->
                posted.add("$query:${rows.joinToString { it.id }}")
                done?.countDown()
            }
            type.getMethod("search", String::class.java, kotlin.jvm.functions.Function1::class.java)
                .invoke(index, query, callback)
        }
        try {
            ui { replace(listOf("slow", "alpha", "beta")); search("alpha") }
            check(blocked.await(5, TimeUnit.SECONDS))
            val latest = CountDownLatch(1)
            ui { repeat(50) { search("old$it") }; search("BETA", latest) }
            release.countDown()
            check(latest.await(10, TimeUnit.SECONDS))
            ui { check(posted == listOf("BETA:beta")); check(resolved.get() == 3) }
            val title = CountDownLatch(1)
            ui { search("Catalog beta", title) }
            check(title.await(5, TimeUnit.SECONDS))
            ui { check(posted.last() == "Catalog beta:beta"); check(resolved.get() == 3) }
            // Queue work and replace its source before its UI result can run.
            val fresh = CountDownLatch(1)
            ui { search("alpha"); replace(listOf("new")); search("new", fresh) }
            check(fresh.await(5, TimeUnit.SECONDS))
            ui { check(posted.last() == "new:new"); check(posted.none { it.startsWith("alpha:") }) }
            ui { search("new"); type.getMethod("cancelQuery").invoke(index) }
            Thread.sleep(100)
            ui { check(posted.size == 3) }
            ui { search("new"); type.getMethod("close").invoke(index) }
            Thread.sleep(100)
            ui { check(posted.size == 3) }
        } finally {
            release.countDown()
            ui { type.getMethod("close").invoke(index) }
        }
    }
}
