package com.mrjackspade.kairo.frontend

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Fetches artwork over HTTP for [CatalogArtworkStore], spacing requests by the server's rate-limit
 * headers and telling a throttled request ([Throttled]) from a failed one (Kairo#35).
 */
class ArtworkFetcher(private val maxBytes: Int) {
    /** A server asking us to slow down: not a failed image. [retryAt] is when it said to resume, if it did. */
    class Throttled(val host: String, val retryAt: Long?) : IOException("$host is limiting requests")

    /** When the next request may start, spread by the server's rate-limit headers. */
    @Volatile private var nextRequestAt = 0L

    /** The body of [url], waiting first for the pace the server asked for. */
    fun fetch(url: String, cancelled: AtomicBoolean): ByteArray {
        waitUntil(nextRequestAt, cancelled)
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 10000
        connection.readTimeout = 20000
        try {
            val code = connection.responseCode
            val now = System.currentTimeMillis()
            val remaining = connection.getHeaderField("x-ratelimit-remaining")?.toLongOrNull()
            val reset = connection.getHeaderField("x-ratelimit-reset")?.toLongOrNull()?.times(1000)
            if (remaining != null && remaining > 0 && reset != null && reset > now)
                nextRequestAt = now + (reset - now) / remaining
            if (code == 429 || (code == HttpURLConnection.HTTP_FORBIDDEN &&
                    (remaining == 0L || connection.getHeaderField("retry-after") != null)))
                throw Throttled(connection.url.host, retryAfter(connection.getHeaderField("retry-after"), now)
                    ?: reset?.takeIf { remaining == 0L })
            require(code == HttpURLConnection.HTTP_OK) { "Image server returned $code" }
            require(connection.contentLengthLong <= maxBytes) { "Image is too large" }
            val bytes = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    checkCancelled(cancelled)
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(bytes.size() + count <= maxBytes) { "Image is too large" }
                    bytes.write(buffer, 0, count)
                }
            }
            return bytes.toByteArray()
        } finally { connection.disconnect() }
    }

    companion object {
        /** Sleeps until [time], a slice at a time so Stop takes effect at once. */
        fun waitUntil(time: Long, cancelled: AtomicBoolean) {
            while (true) {
                checkCancelled(cancelled)
                val left = time - System.currentTimeMillis()
                if (left <= 0) return
                Thread.sleep(minOf(left, 200L))
            }
        }

        fun checkCancelled(cancelled: AtomicBoolean) {
            if (cancelled.get()) throw CancellationException("Artwork download cancelled")
        }

        /** Retry-After as seconds or an HTTP date, as epoch milliseconds. */
        private fun retryAfter(value: String?, now: Long): Long? {
            val text = value?.trim() ?: return null
            text.toLongOrNull()?.let { return now + it * 1000 }
            return java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US)
                .parse(text)?.time
        }
    }
}
