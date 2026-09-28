package com.mrjackspade.kairo.frontend

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Downloads and atomically keeps a validated catalog snapshot for the installed APK. */
class CatalogSnapshotStore(
    context: Context,
    fileName: String,
    private val url: String,
    private val maxBytes: Long,
    private val validate: (File) -> Unit
) {
    private val file = File(context.filesDir, fileName)
    private val marker = File(context.filesDir, "$fileName.apk")
    private val cacheDir = context.cacheDir
    private val apkInstallTime = context.packageManager
        .getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
    private var active = readSaved()

    @Synchronized fun activeFile(): File? = active

    /** Call from a worker thread. Validation and writes finish before the new file becomes active. */
    fun download(): Boolean {
        val temporary = File.createTempFile("catalog-", ".tmp", cacheDir)
        try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 10000
                readTimeout = 20000
            }
            try {
                require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                    "Catalog server returned ${connection.responseCode}"
                }
                require(connection.contentLengthLong <= maxBytes) { "Catalog update is too large" }
                connection.inputStream.use { input ->
                    temporary.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= maxBytes) { "Catalog update is too large" }
                            output.write(buffer, 0, count)
                        }
                    }
                }
            } finally { connection.disconnect() }
            validate(temporary)
            val checksum = digest(temporary)
            synchronized(this) {
                if (active != null && temporary.length() == file.length() &&
                    checksum == digest(file)) return false
                val atomic = AtomicFile(file)
                val output = atomic.startWrite()
                try {
                    temporary.inputStream().use { it.copyTo(output) }
                    atomic.finishWrite(output)
                } catch (error: Exception) {
                    atomic.failWrite(output)
                    throw error
                }
                val markerAtomic = AtomicFile(marker)
                val markerOutput = markerAtomic.startWrite()
                try {
                    markerOutput.write("$apkInstallTime:$checksum".toByteArray(Charsets.UTF_8))
                    markerAtomic.finishWrite(markerOutput)
                } catch (error: Exception) {
                    markerAtomic.failWrite(markerOutput)
                    throw error
                }
                active = file
            }
            return true
        } finally { temporary.delete() }
    }

    private fun readSaved(): File? = try {
        if (!file.isFile || file.length() > maxBytes ||
            AtomicFile(marker).readFully().toString(Charsets.UTF_8) !=
                "$apkInstallTime:${digest(file)}") null
        // The checksum covers the exact bytes validated before the atomic save.
        // Re-parsing a large catalog on the UI thread would delay every launch.
        else file
    } catch (_: Exception) { null }

    private fun digest(source: File): String {
        val sha = MessageDigest.getInstance("SHA-256")
        source.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                sha.update(buffer, 0, count)
            }
        }
        return sha.digest().joinToString("") { "%02x".format(it) }
    }
}
