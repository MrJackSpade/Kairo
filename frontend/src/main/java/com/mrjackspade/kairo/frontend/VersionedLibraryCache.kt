package com.mrjackspade.kairo.frontend

import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/** Bounded, atomic JSON cache with a product-owned entry codec and validator. */
class VersionedLibraryCache<T>(
    private val file: File,
    private val version: Int,
    private val collectionName: String,
    private val maxBytes: Int,
    private val maxEntries: Int,
    private val decode: (JSONObject) -> T?,
    private val encode: (T) -> JSONObject
) {
    data class Snapshot<T>(val treeUri: String, val entries: List<T>, val root: JSONObject)

    private fun raw(): JSONObject? = try {
        val output = ByteArrayOutputStream()
        AtomicFile(file).openRead().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= maxBytes) { "Library cache is too large" }
                output.write(buffer, 0, count)
            }
        }
        JSONObject(output.toString(Charsets.UTF_8.name()))
    } catch (_: Exception) { null }

    fun read(): Snapshot<T>? {
        val root = raw() ?: return null
        if (root.optInt("schemaVersion") != version) return null
        val treeUri = root.optString("treeUri").takeIf { it.startsWith("content://") }
            ?: return null
        val values = root.optJSONArray(collectionName) ?: return null
        if (values.length() > maxEntries) return null
        val entries = (0 until values.length()).mapNotNull { index ->
            values.optJSONObject(index)?.let { runCatching { decode(it) }.getOrNull() }
        }
        return Snapshot(treeUri, entries, root)
    }

    /** Never offer entries from another selected document tree as this library's cache. */
    fun readForTree(treeUri: String): Snapshot<T>? =
        read()?.takeIf { it.treeUri == treeUri }

    fun write(treeUri: String, entries: List<T>, extras: JSONObject = JSONObject()) {
        require(treeUri.startsWith("content://")) { "Invalid library folder" }
        require(entries.size <= maxEntries) { "Too many library entries" }
        val existing = raw()
        require(!file.isFile || file.length() <= maxBytes) {
            "Library cache is too large"
        }
        require(existing == null || existing.optInt("schemaVersion") <= version) {
            "Library cache uses a newer schema; update the app before scanning"
        }
        val values = JSONArray()
        entries.forEach { values.put(encode(it)) }
        val root = JSONObject(extras.toString()).put("schemaVersion", version)
            .put("treeUri", treeUri).put(collectionName, values)
        val bytes = root.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= maxBytes) { "Library cache is too large" }
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            stream.write(bytes)
            atomic.finishWrite(stream)
        } catch (failure: Exception) {
            atomic.failWrite(stream)
            throw failure
        }
    }
}
