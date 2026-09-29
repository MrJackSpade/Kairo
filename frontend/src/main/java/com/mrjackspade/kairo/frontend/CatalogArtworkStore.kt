package com.mrjackspade.kairo.frontend

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
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

    fun open(path: String): InputStream = if (bundled(path)) context.assets.open(path)
        else fileFor(path).inputStream()

    /** Copies a selected image into app storage; its URI permission need not persist. */
    fun importLocal(input: InputStream): String {
        val bytes = ArtworkImages.read(input)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
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

    fun download(path: String, url: String, cancelled: AtomicBoolean) {
        require(validImageUrl(url)) { "Unsupported artwork URL" }
        val target = fileFor(path)
        if (availablePath(path) != null) return
        checkCancelled(cancelled)
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 10000
        connection.readTimeout = 20000
        try {
            require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "Image server returned ${connection.responseCode}"
            }
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

    companion object {
        private const val MAX_DOWNLOAD_BYTES = 16 * 1024 * 1024
        private const val MAX_SOURCE_DIMENSION = 16384
        private const val MAX_HEIGHT = 360
        private const val MAX_WIDTH = 720
    }
}
