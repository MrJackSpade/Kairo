package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.util.Log
import android.widget.Toast
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/** Drives the shared library's cancellable missing-artwork download flow. */
class CatalogArtworkDownloadController<Game : LibraryItem, Source>(
    private val activity: Activity,
    private val screen: LibraryScreen<Game>,
    private val entries: () -> List<Game>,
    private val missing: (List<Game>) -> List<Source>,
    private val download: (Source, AtomicBoolean) -> Unit
) {
    @Volatile private var running = false
    private var cancelled = AtomicBoolean(false)

    fun cancel() { cancelled.set(true) }

    fun start() {
        if (running) { toast("Images are already downloading"); return }
        val games = entries().toList()
        if (games.none { it.playable }) { toast("Add games to the library first"); return }
        running = true
        val stop = AtomicBoolean(false)
        cancelled = stop
        screen.showArtworkProgress(0, 0, 0)
        Thread {
            var completed = 0
            var downloaded = 0
            var failures = 0
            var total = 0
            var errorMessage: String? = null
            try {
                val sources = missing(games)
                total = sources.size
                activity.runOnUiThread {
                    if (!activity.isDestroyed) screen.showArtworkProgress(0, total, 0)
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
                        Log.w("Kairo", "Artwork download failed: $source", error)
                    }
                    completed++
                    val progress = completed
                    val failed = failures
                    activity.runOnUiThread {
                        if (!activity.isDestroyed) screen.showArtworkProgress(progress, total, failed)
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
                else -> "Downloaded $downloaded images · $failures failed. Tap again to retry."
            }
            activity.runOnUiThread {
                running = false
                if (!activity.isDestroyed) {
                    screen.refreshArtwork()
                    screen.finishArtworkDownload(result)
                }
            }
        }.apply { name = "Kairo-catalog-artwork"; start() }
    }

    private fun toast(message: String) =
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
}
