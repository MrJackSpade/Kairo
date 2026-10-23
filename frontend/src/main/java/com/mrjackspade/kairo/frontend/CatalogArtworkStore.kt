package com.mrjackspade.kairo.frontend

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
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

    private val fetcher = ArtworkFetcher(MAX_DOWNLOAD_BYTES)

    fun download(path: String, url: String, cancelled: AtomicBoolean) {
        require(validImageUrl(url)) { "Unsupported artwork URL" }
        val target = fileFor(path)
        if (availablePath(path) != null) return
        val bytes = fetcher.fetch(url, cancelled)
        val image = thumbnail(bytes)
        try {
            ArtworkFetcher.checkCancelled(cancelled)
            target.parentFile?.mkdirs()
            val atomic = AtomicFile(target)
            val output = atomic.startWrite()
            try {
                check(image.compress(Bitmap.CompressFormat.WEBP, 82, output)) {
                    "Could not encode image"
                }
                ArtworkFetcher.checkCancelled(cancelled)
                atomic.finishWrite(output)
            } catch (error: Exception) {
                atomic.failWrite(output)
                throw error
            }
        } finally { image.recycle() }
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

    companion object {
        private const val MAX_DOWNLOAD_BYTES = 16 * 1024 * 1024
        private const val MAX_SOURCE_DIMENSION = 16384
        private const val MAX_HEIGHT = 360
        private const val MAX_WIDTH = 720
    }
}
