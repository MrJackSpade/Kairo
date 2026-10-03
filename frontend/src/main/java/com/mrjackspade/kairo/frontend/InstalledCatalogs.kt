package com.mrjackspade.kairo.frontend

import android.content.Context
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
    private val validateData: (JSONObject) -> Unit,
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
                    val catalog = inspect(file)
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
            val output = java.io.ByteArrayOutputStream()
            copyBounded(it, output, MAX_IMAGE)
            output.toByteArray().inputStream()
        }
    }
    @Synchronized fun close() { archives.values.forEach { it.close() }; archives.clear() }

    @Synchronized fun importFile(input: InputStream, task: CatalogUpdateTask = CatalogUpdateTask()): String {
        val temporary = File.createTempFile("catalog-import-", ".zip", temporaryDirectory)
        try {
            task.report(CatalogUpdateState.IMPORTING)
            input.use { source -> temporary.outputStream().use {
                copyBounded(source, it, MAX_ARCHIVE, task)
                it.fd.sync()
            } }
            task.report(CatalogUpdateState.APPLYING)
            val candidate = inspect(temporary)
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
                val downloadedChecksum = download(url, temporary, size, task)
                require(temporary.length() == size && downloadedChecksum == checksum) { "Catalog checksum mismatch" }
                task.report(CatalogUpdateState.APPLYING)
                val candidate = inspect(temporary)
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
        // Both files are on app-private storage. Rename replaces the old generation
        // atomically without copying hundreds of megabytes a second time.
        android.system.Os.rename(candidate.file.absolutePath, destination.absolutePath)
        installed = (installed + (candidate.id to candidate.copy(file = destination))).toSortedMap()
        changed()
    }

    private fun inspect(file: File): Catalog = ZipFile(file).use { zip ->
        require(file.length() in 1..MAX_ARCHIVE && zip.size() in 2..20004) { "Invalid catalog size" }
        fun read(name: String, limit: Long): ByteArray {
            val entry = zip.getEntry(name) ?: error("Catalog is missing $name")
            require(entry.size in 1..limit) { "Invalid catalog entry size" }
            return zip.getInputStream(entry).use { input ->
                val output = java.io.ByteArrayOutputStream()
                copyBounded(input, output, limit)
                output.toByteArray()
            }
        }
        // New packages separate the small runtime header/index from the publishing
        // inventory. Older manually downloaded packages remain readable too.
        val indexed = zip.getEntry("runtime.json") != null
        val manifest = JSONObject(read(if (indexed) "runtime.json" else "catalog.json",
            4L * 1024 * 1024).toString(Charsets.UTF_8))
        require(manifest.optInt("schemaVersion") == 1 && manifest.optString("product") == product)
        val id = manifest.getString("id")
        val name = manifest.getString("name")
        val revision = manifest.getLong("revision")
        val source = manifest.getString("updateManifest")
        require(id.matches(Regex("[a-z0-9][a-z0-9-]{0,63}")) && name.length in 1..100 &&
            name.none { it.isISOControl() } && revision > 0 && secureUrl(source)) { "Invalid catalog identity" }
        val data = JSONObject(read("data.json", MAX_DATA).toString(Charsets.UTF_8))
        // Index names are looked up inside the ZIP, never extracted to filesystem paths.
        val images = if (indexed) read("artwork.idx", 4L * 1024 * 1024).toString(Charsets.UTF_8)
            .lineSequence().filter { it.isNotEmpty() }.toSet()
        else manifest.getJSONObject("files").keys().asSequence().filter { it != "data.json" }.toSet()
        validateData(data)
        Catalog(id, name, revision, source, data, images, file)
    }

    private fun fetch(url: String, limit: Long, task: CatalogUpdateTask): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        connect(url) { connection -> connection.inputStream.use { copyBounded(it, output, limit, task) } }
        return output.toByteArray()
    }
    private fun download(url: String, file: File, limit: Long, task: CatalogUpdateTask): String {
        val sha = MessageDigest.getInstance("SHA-256")
        connect(url) { connection ->
            require(connection.contentLengthLong <= limit)
            connection.inputStream.use { input -> file.outputStream().use {
                copyBounded(input, it, limit, task, sha)
                it.fd.sync()
            } }
        }
        return sha.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    private fun <T> connect(url: String, redirects: Int = 0, block: (HttpURLConnection) -> T): T {
        require(secureUrl(url))
        require(redirects <= 5) { "Too many catalog redirects" }
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 10000
        connection.readTimeout = 30000
        try {
            if (connection.responseCode in setOf(301, 302, 303, 307, 308)) {
                val location = connection.getHeaderField("Location") ?: error("Missing redirect location")
                return connect(URI(url).resolve(location).toString(), redirects + 1, block)
            }
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
        private fun copyBounded(input: InputStream, output: java.io.OutputStream, limit: Long,
                                task: CatalogUpdateTask? = null, sha: MessageDigest? = null) {
            val buffer = ByteArray(65536)
            var total = 0L
            while (true) {
                task?.ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= limit) { "Catalog exceeds size limit" }
                sha?.update(buffer, 0, count)
                output.write(buffer, 0, count)
            }
        }
    }
}
