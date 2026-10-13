package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.util.Log
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

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
    /** Progress for the Download missing images row, such as "12 / 40". */
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
        show(0, 0, 0)
        Thread {
            var completed = 0
            var downloaded = 0
            var failures = 0
            var total = 0
            var errorMessage: String? = null
            val reasons = linkedMapOf<String, Int>()
            try {
                val sources = missing(games)
                total = sources.size
                activity.runOnUiThread {
                    if (!activity.isDestroyed && !stop.get()) show(0, total, 0)
                }
                for (source in sources) {
                    if (stop.get()) break
                    try {
                        download(source, stop)
                        downloaded++
                    } catch (_: CancellationException) {
                        break
                    } catch (error: Exception) {
                        failures++
                        val reason = reason(error)
                        reasons[reason] = (reasons[reason] ?: 0) + 1
                        Log.e("Kairo", "Artwork download failed: $source", error)
                    }
                    completed++
                    val done = completed
                    val failed = failures
                    activity.runOnUiThread {
                        if (!activity.isDestroyed && !stop.get()) show(done, total, failed)
                    }
                }
            } catch (error: Exception) {
                errorMessage = error.message ?: "Unknown error"
            }
            val result = when {
                errorMessage != null -> "Image download failed: $errorMessage"
                total == 0 -> "No missing catalog images for this library"
                stop.get() -> "Image download stopped · $downloaded saved"
                failures == 0 -> "Downloaded $downloaded images"
                else -> "Downloaded $downloaded images · $failures failed: " + summary(reasons)
            }
            activity.runOnUiThread {
                running = false
                progress = ""
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

    private fun summary(reasons: Map<String, Int>): String {
        val (top, _) = reasons.entries.maxBy { it.value }.toPair()
        return if (reasons.size == 1) top else "$top (and ${reasons.size - 1} other errors)"
    }

    private fun show(completed: Int, total: Int, failures: Int) {
        progress = if (total == 0) "" else "$completed / $total"
        screen.tasks.show(TASK, if (total == 0) "Checking library artwork…"
            else "Downloading images $completed/$total" + (if (failures == 0) "" else " · $failures failed"),
            PRIORITY, ::cancel)
        changed()
    }

    private companion object {
        const val TASK = "images"
        const val PRIORITY = 3
    }
}
