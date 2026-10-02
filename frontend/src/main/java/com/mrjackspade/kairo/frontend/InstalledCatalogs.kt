package com.mrjackspade.kairo.frontend

import android.content.Context
import android.graphics.BitmapFactory
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Data archives selected by the user. No registry, discovery endpoint, or built-in sources. */
class InstalledCatalogs(
    context: Context,
    private val product: String,
    private val validateData: (JSONObject) -> Set<String>,
    private val changed: () -> Unit
) {
    data class Catalog(val id: String, val name: String, val revision: Long,
                       val source: String, val data: JSONObject, val images: Set<String>,
                       internal val file: File)
    private val directory = File(context.filesDir, "installed-catalogs").apply { mkdirs() }
    private val temporaryDirectory = context.cacheDir
    @Volatile private var installed: Map<String, Catalog> = linkedMapOf()
    private val archives = HashMap<String, ZipFile>()

    init {
        directory.listFiles()?.filter { it.name.endsWith(".zip") || it.name.endsWith(".zip.bak") }
            ?.map { it.name.removeSuffix(".bak") }?.distinct()?.sorted()?.forEach { name ->
                val file = File(directory, name)
                runCatching {
                    AtomicFile(file).openRead().close()
                    val catalog = inspect(file, false)
                    require(file.name == "${catalog.id}.zip")
                    installed = installed + (catalog.id to catalog)
                }
            }
    }

    fun catalogs(): List<Catalog> = installed.values.toList()
    fun sources(id: String): List<CatalogFieldLayers.Source> =
        installed.values.map { CatalogFieldLayers.Source(it.name, it.data.optJSONObject("games")?.optJSONObject(id)) }
    fun artwork(path: String?): String? = path?.takeIf { p -> installed.values.any { p in it.images } }
    @Synchronized fun openArtwork(path: String): InputStream? {
        val catalog = installed.values.lastOrNull { path in it.images } ?: return null
        // Retain the directory index, but copy the bounded image before releasing the lock.
        // Replacement/removal closes the old generation before touching its archive.
        val zip = archives.getOrPut(catalog.id) { ZipFile(catalog.file) }
        return zip.getInputStream(zip.getEntry(path)).use {
            it.readBytes().inputStream()
        }
    }
    @Synchronized fun close() { archives.values.forEach { it.close() }; archives.clear() }

    @Synchronized fun importFile(input: InputStream): String {
        val temporary = File.createTempFile("catalog-import-", ".zip", temporaryDirectory)
        try {
            input.use { source -> temporary.outputStream().use { copyBounded(source, it, MAX_ARCHIVE) } }
            val candidate = inspect(temporary, true)
            installed[candidate.id]?.let { previous ->
                require(candidate.source == previous.source && candidate.revision > previous.revision) {
                    "Catalog already installed or source/revision changed"
                }
            }
            activate(candidate)
            return candidate.name
        } finally { temporary.delete() }
    }

    @Synchronized fun remove(id: String) {
        require(installed.containsKey(id)) { "Catalog is not installed" }
        archives.remove(id)?.close()
        AtomicFile(File(directory, "$id.zip")).delete()
        require(!File(directory, "$id.zip").exists()) { "Could not remove catalog" }
        installed = installed - id
        changed()
    }

    /** Each catalog updates independently; a failed replacement retains that catalog's old archive. */
    @Synchronized fun update(task: CatalogUpdateTask = CatalogUpdateTask()): Boolean {
        var updated = false
        for (old in installed.values.toList()) {
            task.report(CatalogUpdateState.CHECKING)
            val bytes = fetch(old.source, 16384, task)
            val manifest = JSONObject(bytes.toString(Charsets.UTF_8))
            require(manifest.optInt("schemaVersion") == 1 && manifest.optString("id") == old.id &&
                manifest.optString("product") == product) { "Wrong catalog update identity" }
            val revision = manifest.getLong("revision")
            require(revision >= old.revision) { "Catalog revision went backwards" }
            if (revision == old.revision) continue
            val size = manifest.getLong("size")
            val checksum = manifest.getString("sha256")
            val url = manifest.getString("archive")
            require(size in 1..MAX_ARCHIVE && checksum.matches(HASH) && secureUrl(url))
            val temporary = File.createTempFile("catalog-update-", ".zip", temporaryDirectory)
            try {
                task.report(CatalogUpdateState.DOWNLOADING)
                download(url, temporary, size, task)
                require(temporary.length() == size && digest(temporary) == checksum) { "Catalog checksum mismatch" }
                task.report(CatalogUpdateState.APPLYING)
                val candidate = inspect(temporary, true)
                require(candidate.id == old.id && candidate.source == old.source && candidate.revision == revision) {
                    "Catalog update changed identity or update source"
                }
                task.ensureActive()
                activate(candidate)
                updated = true
            } finally { temporary.delete() }
        }
        return updated
    }

    private fun activate(candidate: Catalog) {
        val destination = File(directory, "${candidate.id}.zip")
        archives.remove(candidate.id)?.close()
        val atomic = AtomicFile(destination)
        val output = atomic.startWrite()
        try {
            candidate.file.inputStream().use { it.copyTo(output) }
            atomic.finishWrite(output)
        } catch (error: Exception) { atomic.failWrite(output); throw error }
        installed = (installed + (candidate.id to candidate.copy(file = destination))).toSortedMap()
        changed()
    }

    private fun inspect(file: File, full: Boolean): Catalog = ZipFile(file).use { zip ->
        require(file.length() in 1..MAX_ARCHIVE && zip.size() in 2..20002) { "Invalid catalog size" }
        val entries = zip.entries().asSequence().toList()
        require(entries.map { it.name }.distinct().size == entries.size && entries.none { it.isDirectory })
        fun read(name: String, limit: Long): ByteArray {
            val entry = zip.getEntry(name) ?: error("Catalog is missing $name")
            require(entry.size in 1..limit) { "Invalid catalog entry size" }
            return zip.getInputStream(entry).use { input ->
                val output = java.io.ByteArrayOutputStream()
                copyBounded(input, output, limit)
                output.toByteArray()
            }
        }
        val manifest = JSONObject(read("catalog.json", 4L * 1024 * 1024).toString(Charsets.UTF_8))
        require(manifest.optInt("schemaVersion") == 1 && manifest.optString("product") == product)
        val id = manifest.getString("id")
        val name = manifest.getString("name")
        val revision = manifest.getLong("revision")
        val source = manifest.getString("updateManifest")
        require(id.matches(Regex("[a-z0-9][a-z0-9-]{0,63}")) && name.length in 1..100 &&
            name.none { it.isISOControl() } && revision > 0 && secureUrl(source)) { "Invalid catalog identity" }
        val inventory = manifest.getJSONObject("files")
        require(inventory.keys().asSequence().toSet() + "catalog.json" == entries.map { it.name }.toSet()) {
            "Unexpected catalog files"
        }
        var total = 0L
        for (path in inventory.keys()) {
            require(path == "data.json" || path.matches(Regex("art/catalog/[a-zA-Z0-9/_-]+(?:[.][a-zA-Z0-9_-]+)*[.]webp"))) {
                "Unsafe catalog path"
            }
            require(!path.contains("..") && !path.contains("/local/")) { "Reserved artwork path" }
            val entry = zip.getEntry(path)
            val spec = inventory.getJSONObject(path)
            val size = spec.getLong("size")
            require(size in 1..(if (path == "data.json") MAX_DATA else MAX_IMAGE) && entry.size == size &&
                spec.getString("sha256").matches(HASH)) { "Invalid catalog file inventory" }
            total += size
            require(total <= MAX_ARCHIVE) { "Expanded catalog is too large" }
            if (full || path == "data.json") {
                val bytes = read(path, size)
                require(bytes.size.toLong() == size && digest(bytes) == spec.getString("sha256")) { "Catalog file checksum mismatch" }
                if (path != "data.json") {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    require(options.outMimeType == "image/webp" && options.outWidth in 1..4096 &&
                        options.outHeight in 1..4096 && options.outWidth.toLong() * options.outHeight <= 4_000_000) {
                        "Invalid catalog image"
                    }
                }
            }
        }
        val data = JSONObject(read("data.json", MAX_DATA).toString(Charsets.UTF_8))
        val images = inventory.keys().asSequence().filter { it != "data.json" }.toSet()
        require(validateData(data) == images) { "Artwork does not match catalog records" }
        Catalog(id, name, revision, source, data, images, file)
    }

    private fun fetch(url: String, limit: Long, task: CatalogUpdateTask): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        connect(url) { connection -> connection.inputStream.use { copyBounded(it, output, limit, task) } }
        return output.toByteArray()
    }
    private fun download(url: String, file: File, limit: Long, task: CatalogUpdateTask) = connect(url) { connection ->
        require(connection.contentLengthLong <= limit)
        connection.inputStream.use { input -> file.outputStream().use { copyBounded(input, it, limit, task) } }
    }
    private fun <T> connect(url: String, block: (HttpURLConnection) -> T): T {
        require(secureUrl(url))
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 10000
        connection.readTimeout = 30000
        try {
            require(connection.responseCode == 200) { "Catalog server returned ${connection.responseCode}" }
            return block(connection)
        } finally { connection.disconnect() }
    }
    companion object {
        private const val MAX_ARCHIVE = 512L * 1024 * 1024
        private const val MAX_DATA = 32L * 1024 * 1024
        private const val MAX_IMAGE = 4L * 1024 * 1024
        private val HASH = Regex("[0-9a-f]{64}")
        private fun secureUrl(value: String): Boolean = runCatching { URI(value).let {
            value.length <= 2048 && it.scheme == "https" && !it.host.isNullOrBlank() &&
                it.userInfo == null && it.fragment == null && it.port in setOf(-1, 443)
        } }.getOrDefault(false)
        private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        private fun digest(file: File): String {
            val sha = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input -> val buffer = ByteArray(65536)
                while (true) { val count = input.read(buffer); if (count < 0) break; sha.update(buffer, 0, count) }
            }
            return sha.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        }
        private fun copyBounded(input: InputStream, output: java.io.OutputStream, limit: Long,
                                task: CatalogUpdateTask? = null) {
            val buffer = ByteArray(65536)
            var total = 0L
            while (true) {
                task?.ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= limit) { "Catalog exceeds size limit" }
                output.write(buffer, 0, count)
            }
        }
    }
}
