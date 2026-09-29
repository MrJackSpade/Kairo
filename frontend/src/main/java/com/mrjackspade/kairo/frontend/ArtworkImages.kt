package com.mrjackspade.kairo.frontend

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Bounded image decoding for local selections and optional larger online previews. */
object ArtworkImages {
    private const val MAX_BYTES = 16 * 1024 * 1024

    fun read(input: InputStream): ByteArray {
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(bytes.size() + count <= MAX_BYTES) { "Image is too large" }
            bytes.write(buffer, 0, count)
        }
        return bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0 &&
            bounds.outWidth.toLong() * bounds.outHeight <= 32_000_000L) {
            "Invalid image dimensions"
        }
        val options = BitmapFactory.Options().apply { inSampleSize = 1 }
        while (bounds.outWidth / options.inSampleSize > 2048 ||
            bounds.outHeight / options.inSampleSize > 2048) options.inSampleSize *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: error("Could not decode image")
    }

    fun fetch(url: String, validUrl: (String) -> Boolean): Bitmap? {
        if (!validUrl(url)) return null
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 10000
        connection.readTimeout = 20000
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK ||
                connection.contentLengthLong > MAX_BYTES) return null
            return connection.inputStream.use { decode(read(it)) }
        } finally { connection.disconnect() }
    }
}
