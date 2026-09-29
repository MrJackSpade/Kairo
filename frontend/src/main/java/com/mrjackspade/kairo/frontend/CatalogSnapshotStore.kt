package com.mrjackspade.kairo.frontend

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.json.JSONObject

/** Checks a small revision file before downloading a validated catalog snapshot. */
class CatalogSnapshotStore(
    context: Context,
    fileName: String,
    private val url: String,
    private val metadataUrl: String,
    private val maxBytes: Long,
    private val validate: (File) -> Unit
) {
    private val file = File(context.filesDir, fileName)
    private val marker = File(context.filesDir, "$fileName.apk")
    private val cacheDir = context.cacheDir
    private val apkInstallTime = context.packageManager
        .getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
    private var knownChecksum: String? = null
    private var active = readSaved()

    @Synchronized fun activeFile(): File? = active

    /** Call from a worker thread. Unchanged metadata does no archive work. */
    fun download(): Boolean {
        val revision = fetchRevision()
        if (revision.checksum == knownChecksum) return false
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
            require(temporary.length() == revision.size &&
                digest(temporary) == revision.checksum) { "Catalog archive does not match metadata" }
            validate(temporary)
            synchronized(this) {
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
                    markerOutput.write("$apkInstallTime:${revision.checksum}".toByteArray(Charsets.UTF_8))
                    markerAtomic.finishWrite(markerOutput)
                } catch (error: Exception) {
                    markerAtomic.failWrite(markerOutput)
                    throw error
                }
                active = file
                knownChecksum = revision.checksum
            }
            return true
        } finally { temporary.delete() }
    }

    private data class Revision(val checksum: String, val size: Long)

    private fun fetchRevision(): Revision {
        val connection = (URL(metadataUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 10000
            readTimeout = 10000
        }
        try {
            require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "Catalog metadata server returned ${connection.responseCode}"
            }
            require(connection.contentLengthLong <= 4096) { "Catalog metadata is too large" }
            val content = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 4096) { "Catalog metadata is too large" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val json = JSONObject(content.toString(Charsets.UTF_8))
            val checksum = json.optString("sha256")
            val size = json.optLong("size", -1)
            require(json.optInt("schemaVersion") == 1 &&
                json.optString("archive") == URL(url).path.substringAfterLast('/') &&
                checksum.matches(Regex("[0-9a-f]{64}")) && size in 1..maxBytes) {
                "Invalid catalog metadata"
            }
            return Revision(checksum, size)
        } finally { connection.disconnect() }
    }

    private fun readSaved(): File? = try {
        if (!file.isFile || file.length() > maxBytes) null
        else {
            val saved = AtomicFile(marker).readFully().toString(Charsets.UTF_8)
            val checksum = saved.substringAfterLast(':')
            if (!checksum.matches(Regex("[0-9a-f]{64}"))) null
            else if (saved.startsWith("$apkInstallTime:") && checksum != digest(file)) null
            else {
                knownChecksum = checksum
                if (saved.startsWith("$apkInstallTime:")) file else null
            }
        }
        // The checksum covers the exact bytes validated before the atomic save.
        // A prior APK's snapshot remains inactive, but its revision avoids a download.
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
