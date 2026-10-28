package com.mrjackspade.kairo.frontend

import android.content.Context
import org.json.JSONObject

/**
 * Build-selected data documents. Only files physically shipped in this build are read. The build
 * compiles each part to binary (shared/gradle/catalog-binary.gradle); a document is decoded from
 * its own byte range without tokenizing text.
 */
class CatalogParts(context: Context) {
    private val assets = context.assets
    private val indexes by lazy {
        assets.list("catalog")!!.filter { it.endsWith(".idx") }.sorted().associate { name ->
            val root = assets.open("catalog/$name").use { JSONObject(it.bufferedReader().readText()) }
            require(root.getInt("schemaVersion") == 2)
            name.removeSuffix(".idx") to root.getJSONObject("documents")
        }
    }
    private class Document(val json: JSONObject, val bytes: Int)
    // Weighted by document size: a count limit let a game's small shard evict the large name
    // index it falls back to, so a library walk re-parsed that index for nearly every game.
    private val cache = object : android.util.LruCache<String, Document>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Document) = value.bytes
    }
    /** Which parts a read takes: every part, only those every build ships, or only the others (adult). */
    private enum class Parts { ALL, MAIN, ADDITIONAL }

    @Synchronized fun read(path: String): JSONObject = read(path, Parts.ALL)
    @Synchronized fun main(path: String): JSONObject = read(path, Parts.MAIN)
    @Synchronized fun additional(path: String): JSONObject = read(path, Parts.ADDITIONAL)

    private fun read(path: String, parts: Parts): JSONObject {
        val key = "$parts:$path"
        cache.get(key)?.let { return it.json }
        val result = JSONObject()
        var bytesRead = 0
        for ((part, index) in indexes) {
            val main = part in MAIN_PARTS
            if (parts == Parts.MAIN && !main || parts == Parts.ADDITIONAL && main) continue
            val range = index.optJSONArray(path) ?: continue
            val offset = range.getLong(0)
            val size = range.getInt(1)
            require(offset >= 0 && size in 2..(16 * 1024 * 1024))
            val bytes = assets.open("catalog/$part.bin").use { input ->
                var remaining = offset
                while (remaining > 0) {
                    val skipped = input.skip(remaining)
                    require(skipped > 0) { "Invalid catalog range" }
                    remaining -= skipped
                }
                val bytes = ByteArray(size)
                var position = 0
                while (position < size) {
                    val count = input.read(bytes, position, size - position)
                    require(count > 0) { "Truncated catalog document" }
                    position += count
                }
                bytes
            }
            bytesRead += size
            merge(result, BinaryDocument(bytes).read())
        }
        cache.put(key, Document(result, bytesRead.coerceAtLeast(1)))
        return result
    }

    private fun merge(target: JSONObject, source: JSONObject) {
        for (key in source.keys()) {
            val value = source.get(key)
            if (value is JSONObject) {
                val child = target.optJSONObject(key) ?: JSONObject().also { target.put(key, it) }
                merge(child, value)
            } else target.put(key, if (key == "kinds" && value is Int) target.optInt(key) or value else value)
        }
    }

    /** One compiled document: a key table, then a value tree naming keys by index. */
    private class BinaryDocument(private val bytes: ByteArray) {
        private var position = 0
        private lateinit var keys: Array<String>

        fun read(): JSONObject {
            keys = Array(int()) { text() }
            return value() as JSONObject
        }

        private fun value(): Any = when (val tag = bytes[position++].toInt()) {
            0 -> JSONObject.NULL
            1 -> false
            2 -> true
            3 -> int()
            4 -> long()
            5 -> Double.fromBits(long())
            6 -> text()
            7 -> JSONObject().also { target ->
                repeat(int()) { target.put(keys[int()], value()) }
            }
            8 -> org.json.JSONArray().also { target -> repeat(int()) { target.put(value()) } }
            else -> error("Unknown catalog value tag $tag")
        }

        private fun int(): Int {
            val value = (bytes[position].toInt() and 255 shl 24) or
                (bytes[position + 1].toInt() and 255 shl 16) or
                (bytes[position + 2].toInt() and 255 shl 8) or (bytes[position + 3].toInt() and 255)
            position += 4
            return value
        }

        private fun long(): Long = (int().toLong() shl 32) or (int().toLong() and 0xffffffffL)

        private fun text(): String {
            val length = int()
            return String(bytes, position, length, Charsets.UTF_8).also { position += length }
        }
    }

    private companion object {
        /** The parts every build ships; the others (adult images and descriptions) only some builds. */
        val MAIN_PARTS = setOf("data", "controls", "art")
        /** Source JSON bytes kept parsed; Kairo98's name indexes and shards fit together. */
        const val CACHE_BYTES = 3 * 1024 * 1024
    }
}
