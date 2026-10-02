package com.mrjackspade.kairo.frontend

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.io.InputStream
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Main-thread owner of asynchronous secondary-library previews in both apps. */
internal class LibraryArtworkLoader(
    private val publish: (SecondaryDisplayCoordinator.LibraryInfo?) -> Unit
) {
    private data class Request(val info: SecondaryDisplayCoordinator.LibraryInfo,
                               val path: String?, val open: ((String) -> InputStream)?)
    private val handler = Handler(Looper.getMainLooper())
    // Catalog artwork paths are immutable: local imports use content hashes and
    // downloads never overwrite an existing path (CatalogArtworkStore).
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private var request: Request? = null
    private var generation = 0
    private var suspended = false
    private var executor: ThreadPoolExecutor? = null
    private var pending: Future<*>? = null

    fun show(info: SecondaryDisplayCoordinator.LibraryInfo?, path: String?,
             open: ((String) -> InputStream)?) {
        val token = ++generation
        pending?.cancel(false)
        executor?.purge()
        request = info?.let { Request(it, path, open) }
        if (info == null) { publish(null); return }
        val cached = path?.let(cache::get)
        val snapshot = info.copy(art = cached ?: info.art,
            hasArtwork = path != null || info.hasArtwork)
        // Cached previews are published with their text in one update, never an
        // empty intermediate frame. Misses reserve the same image bounds.
        publish(snapshot)
        if (path == null || cached != null || open == null || suspended) return
        val worker = executor ?: ThreadPoolExecutor(1, 1, 0L, TimeUnit.SECONDS,
            LinkedBlockingQueue<Runnable>()).also { executor = it }
        pending = worker.submit {
            val bitmap = runCatching {
                open(path).use { BitmapFactory.decodeStream(it, null,
                    BitmapFactory.Options().apply { inSampleSize = 2 }) }
            }.getOrNull()
            handler.post {
                if (token == generation && !suspended && bitmap != null) {
                    cache.put(path, bitmap)
                    publish(snapshot.copy(art = bitmap))
                }
                // Failed images keep their reserved space; old images never leak
                // into the new selection. Do not cache failures: a refresh can retry.
            }
        }
    }

    fun suspend() {
        suspended = true
        ++generation
        pending?.cancel(false)
        pending = null
        executor?.shutdownNow()
        executor = null
    }

    fun resume() {
        if (!suspended) return
        suspended = false
        request?.let { show(it.info, it.path, it.open) }
    }
}
