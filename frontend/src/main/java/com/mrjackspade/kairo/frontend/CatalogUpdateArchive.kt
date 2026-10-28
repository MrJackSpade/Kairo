package com.mrjackspade.kairo.frontend

import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.util.zip.ZipFile

/**
 * A downloaded catalog update (shared/tools/catalog_documents.py update_archive): the app's catalog
 * documents at their catalog paths, as the build bundles them. Each is parsed when first read, so
 * a session holds the documents its games use, not the whole catalog.
 */
class CatalogUpdateArchive(file: File) : Closeable {
    private val zip = ZipFile(file)
    private class Document(val json: JSONObject, val bytes: Int)
    // Least recently read first; weighed by document size, as CatalogParts weighs its cache.
    private val cache = LinkedHashMap<String, Document>(16, 0.75f, true)
    private var cachedBytes = 0

    init {
        try {
            require(document("update-v1.json")?.optInt("updateVersion") == UPDATE_VERSION) { "Unsupported catalog update" }
        } catch (error: Exception) {
            zip.close()
            throw error
        }
    }

    /** The document at [path], or null when the update has none. */
    @Synchronized fun document(path: String): JSONObject? {
        cache[path]?.let { return it.json }
        val entry = zip.getEntry(path) ?: return null
        require(!entry.isDirectory && entry.size in 2..MAX_DOCUMENT_BYTES) { "Invalid catalog document $path" }
        val bytes = zip.getInputStream(entry).use { input ->
            val output = java.io.ByteArrayOutputStream(entry.size.toInt())
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= MAX_DOCUMENT_BYTES) { "Catalog document $path is too large" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        val json = JSONObject(bytes.toString(Charsets.UTF_8))
        cache[path] = Document(json, bytes.size)
        cachedBytes += bytes.size
        val oldest = cache.entries.iterator()
        while (cachedBytes > CACHE_BYTES && cache.size > 1) {
            cachedBytes -= oldest.next().value.bytes
            oldest.remove()
        }
        return json
    }

    @Synchronized override fun close() = zip.close()

    companion object {
        /** The update format this app reads (shared/tools/catalog_documents.py UPDATE_VERSION). */
        const val UPDATE_VERSION = 4
        private const val MAX_DOCUMENT_BYTES = 16L * 1024 * 1024
        /** Source JSON bytes kept parsed, as for the bundled parts (CatalogParts). */
        private const val CACHE_BYTES = 3 * 1024 * 1024

        /** A downloaded update this app reads, with every document [required] in it. */
        fun validate(file: File, vararg required: String) = CatalogUpdateArchive(file).use { archive ->
            for (path in required) requireNotNull(archive.document(path)) { "Catalog update lacks $path" }
        }
    }
}
