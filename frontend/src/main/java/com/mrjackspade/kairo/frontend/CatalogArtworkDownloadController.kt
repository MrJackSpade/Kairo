package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.util.Log
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Drives the shared library's cancellable missing-artwork download flow. */
class CatalogArtworkDownloadController<Game : LibraryItem, Source>(
    private val activity: Activity,
    private val screen: LibraryScreen<Game>,
    private val entries: () -> List<Game>,
    private val missing: (List<Game>) -> List<Source>,
    private val download: (Source, AtomicBoolean) -> Unit,
    private val changed: () -> Unit = {}
) {
    @Volatile var running = false
        private set
    private var cancelled = AtomicBoolean(false)
    /** The Download missing images row's value: "Checking…", "12 / 40", a wait, then the last result. */
    var progress: String = ""
        private set

    fun cancel() {
        if (!running || cancelled.get()) return
        cancelled.set(true)
        screen.tasks.show(TASK, "Stopping image download…", PRIORITY)
        changed()
    }

    fun start() {
        if (running) { Ui.message(activity, "Images are already downloading"); return }
        val games = entries().toList()
        if (games.none { it.playable }) { Ui.message(activity, "Add games to the library first"); return }
        running = true
        val stop = AtomicBoolean(false)
        cancelled = stop
        show("Checking…", "Checking library artwork…")
        Thread {
            val completed = AtomicInteger()
            val saved = AtomicInteger()
            val failed = AtomicInteger()
            var total = 0
            var errorMessage: String? = null
            val reasons = linkedMapOf<String, Int>()
            // Several workers report: only the newest state waits for the main thread.
            val pending = AtomicReference<Pair<String, String>?>()
            fun post(row: String, task: String) {
                if (pending.getAndSet(row to task) == null) activity.runOnUiThread {
                    val latest = pending.getAndSet(null)
                    if (latest != null && !activity.isDestroyed && !stop.get()) show(latest.first, latest.second)
                }
            }
            fun counted() = "Downloading images ${completed.get()}/$total" +
                (if (failed.get() == 0) "" else " · ${failed.get()} failed")
            // A server that asks us to wait holds every worker until then.
            val resumeAt = AtomicLong(0)
            try {
                val sources = missing(games)
                total = sources.size
                post("0 / $total", counted())
                // Each image is downloaded, decoded and encoded again: one at a time left the
                // network and the other cores idle, about one image a second (KairoDos#83).
                ParallelScanWork.map(sources, stop, WORKERS) { _, source ->
                    var backoff = FIRST_BACKOFF_MILLIS
                    while (true) {
                        val wait = resumeAt.get()
                        if (wait > System.currentTimeMillis()) ArtworkFetcher.waitUntil(wait, stop)
                        try {
                            download(source, stop)
                            saved.incrementAndGet()
                        } catch (throttled: ArtworkFetcher.Throttled) {
                            val resume = throttled.retryAt
                                ?: (System.currentTimeMillis() + backoff).also { backoff = (backoff * 2).coerceAtMost(LAST_BACKOFF_MILLIS) }
                            resumeAt.accumulateAndGet(resume, ::maxOf)
                            Log.i("Kairo", "Artwork: ${throttled.host} is limiting requests; waiting until $resume")
                            post("Waiting…", "Waiting for ${throttled.host}'s rate limit…")
                            continue
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (error: Exception) {
                            failed.incrementAndGet()
                            val reason = reason(error)
                            synchronized(reasons) { reasons[reason] = (reasons[reason] ?: 0) + 1 }
                            Log.e("Kairo", "Artwork download failed: $source", error)
                        }
                        break
                    }
                    completed.incrementAndGet()
                    post("${completed.get()} / $total", counted())
                }
            } catch (_: CancellationException) {
            } catch (error: Exception) {
                errorMessage = error.message ?: "Unknown error"
            }
            val downloaded = saved.get()
            val failures = failed.get()
            val result = when {
                errorMessage != null -> "Image download failed: $errorMessage"
                total == 0 -> "No missing catalog images for this library"
                stop.get() -> "Image download stopped · $downloaded saved"
                failures == 0 -> "Downloaded $downloaded images"
                else -> "Downloaded $downloaded images · $failures failed: " + breakdown(reasons)
            }
            val value = when {
                errorMessage != null -> "Failed"
                total == 0 -> "None missing"
                stop.get() -> "Stopped · $downloaded saved"
                failures == 0 -> "$downloaded saved"
                else -> "$downloaded saved · $failures failed"
            }
            activity.runOnUiThread {
                running = false
                progress = value
                if (!activity.isDestroyed) {
                    screen.tasks.hide(TASK)
                    screen.refreshArtwork()
                    Ui.message(activity, result, long = true)
                    changed()
                }
            }
        }.apply { name = "Kairo-catalog-artwork"; start() }
    }

    /** The cause a person can act on: no connection, or the error itself. */
    private fun reason(error: Exception): String = when (error) {
        is java.net.UnknownHostException, is java.net.NoRouteToHostException -> "No internet connection"
        is java.net.SocketTimeoutException -> "The image server did not respond"
        else -> error.message ?: error.javaClass.simpleName
    }

    /** Every reason with its count, most common first. */
    private fun breakdown(reasons: Map<String, Int>): String =
        reasons.entries.sortedByDescending { it.value }.joinToString("; ") { "${it.key} (${it.value})" }

    private fun show(row: String, task: String) {
        progress = row
        screen.tasks.show(TASK, task, PRIORITY, ::cancel)
        changed()
    }

    private companion object {
        const val TASK = "images"
        const val PRIORITY = 3
        /** Images downloaded at once. */
        const val WORKERS = 4
        // Used only when a server limits requests without saying when to resume.
        const val FIRST_BACKOFF_MILLIS = 1_000L
        const val LAST_BACKOFF_MILLIS = 64_000L
    }
}
