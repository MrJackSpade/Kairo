package com.mrjackspade.kairo.frontend

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/** Bundled artwork and persistent, bounded copies downloaded for a user's library. */
class CatalogArtworkStore(
    private val context: Context,
    private val validImageUrl: (String) -> Boolean,
    private val pathPrefix: String = "art/catalog/"
) {
    private val root = File(context.filesDir, "catalog-art")
    private val bundledPaths = android.util.LruCache<String, Boolean>(256)

    fun availablePath(path: String?): String? = path?.takeIf {
        bundled(it) || fileFor(it).isFile
    }

    /** A downloaded or imported copy, else the image bundled in the APK. */
    fun open(path: String): InputStream =
        (if (path.startsWith(pathPrefix)) fileFor(path).takeIf { it.isFile } else null)
            ?.inputStream() ?: context.assets.open(path)

    /** Copies a selected image into app storage; its URI permission need not persist. */
    fun importLocal(input: InputStream): String {
        val bytes = ArtworkImages.read(input)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
        val path = "${pathPrefix}local/$digest.webp"
        val image = thumbnail(bytes)
        try {
            val target = fileFor(path)
            target.parentFile?.mkdirs()
            val atomic = AtomicFile(target)
            val output = atomic.startWrite()
            try {
                check(image.compress(Bitmap.CompressFormat.WEBP, 82, output)) {
                    "Could not encode image"
                }
                atomic.finishWrite(output)
            } catch (error: Exception) {
                atomic.failWrite(output)
                throw error
            }
        } finally { image.recycle() }
        return path
    }

    /** A server asking us to slow down: not a failed image. [retryAt] is when it said to resume, if it did. */
    class Throttled(val host: String, val retryAt: Long?) : IOException("$host is limiting requests")

    /** When the next request may start, spread by the server's rate-limit headers. */
    @Volatile private var nextRequestAt = 0L

    fun download(path: String, url: String, cancelled: AtomicBoolean) {
        require(validImageUrl(url)) { "Unsupported artwork URL" }
        val target = fileFor(path)
        if (availablePath(path) != null) return
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
            require(connection.contentLengthLong <= MAX_DOWNLOAD_BYTES) { "Image is too large" }
            val bytes = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    checkCancelled(cancelled)
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(bytes.size() + count <= MAX_DOWNLOAD_BYTES) { "Image is too large" }
                    bytes.write(buffer, 0, count)
                }
            }
            val image = thumbnail(bytes.toByteArray())
            try {
                checkCancelled(cancelled)
                target.parentFile?.mkdirs()
                val atomic = AtomicFile(target)
                val output = atomic.startWrite()
                try {
                    check(image.compress(Bitmap.CompressFormat.WEBP, 82, output)) {
                        "Could not encode image"
                    }
                    checkCancelled(cancelled)
                    atomic.finishWrite(output)
                } catch (error: Exception) {
                    atomic.failWrite(output)
                    throw error
                }
            } finally { image.recycle() }
        } finally { connection.disconnect() }
    }

    private fun thumbnail(bytes: ByteArray): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth in 1..MAX_SOURCE_DIMENSION &&
            bounds.outHeight in 1..MAX_SOURCE_DIMENSION &&
            bounds.outWidth.toLong() * bounds.outHeight <= 32_000_000L) {
            "Invalid image dimensions"
        }
        var sample = 1
        while (bounds.outWidth / sample > MAX_WIDTH * 2 ||
            bounds.outHeight / sample > MAX_HEIGHT * 2) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Could not decode image")
        val scale = minOf(1.0, MAX_WIDTH.toDouble() / decoded.width,
            MAX_HEIGHT.toDouble() / decoded.height)
        if (scale >= 1.0) return decoded
        val result = Bitmap.createScaledBitmap(decoded,
            (decoded.width * scale).roundToInt().coerceAtLeast(1),
            (decoded.height * scale).roundToInt().coerceAtLeast(1), true)
        if (result !== decoded) decoded.recycle()
        return result
    }

    private fun bundled(path: String): Boolean {
        bundledPaths.get(path)?.let { return it }
        val exists = runCatching { context.assets.open(path).use { true } }.getOrDefault(false)
        bundledPaths.put(path, exists)
        return exists
    }

    private fun fileFor(path: String): File {
        require(path.startsWith(pathPrefix) && !path.contains("..") &&
            !path.contains('\\') && !path.contains(':')) { "Invalid artwork path" }
        val file = File(root, path)
        require(file.canonicalPath.startsWith(root.canonicalPath + File.separator)) {
            "Invalid artwork path"
        }
        return file
    }

    private fun checkCancelled(cancelled: AtomicBoolean) {
        if (cancelled.get()) throw CancellationException("Artwork download cancelled")
    }

    /** Retry-After as seconds or an HTTP date, as epoch milliseconds. */
    private fun retryAfter(value: String?, now: Long): Long? {
        val text = value?.trim() ?: return null
        text.toLongOrNull()?.let { return now + it * 1000 }
        return java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US)
            .parse(text)?.time
    }

    companion object {
        /** Sleeps until [time], a slice at a time so Stop takes effect at once. */
        fun waitUntil(time: Long, cancelled: AtomicBoolean) {
            while (true) {
                if (cancelled.get()) throw CancellationException("Artwork download cancelled")
                val left = time - System.currentTimeMillis()
                if (left <= 0) return
                Thread.sleep(minOf(left, 200L))
            }
        }

        private const val MAX_DOWNLOAD_BYTES = 16 * 1024 * 1024
        private const val MAX_SOURCE_DIMENSION = 16384
        private const val MAX_HEIGHT = 360
        private const val MAX_WIDTH = 720
    }
}
