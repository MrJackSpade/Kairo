package com.mrjackspade.kairo.frontend

import android.content.Context
import org.json.JSONObject

/** Build-selected data documents. Only files physically shipped in this build are read. */
class CatalogParts(context: Context) {
    private val assets = context.assets
    private val indexes by lazy {
        assets.list("catalog")!!.filter { it.endsWith(".idx") }.sorted().associate { name ->
            val root = assets.open("catalog/$name").use { JSONObject(it.bufferedReader().readText()) }
            require(root.getInt("schemaVersion") == 1)
            name.removeSuffix(".idx") to root.getJSONObject("documents")
        }
    }
    private val cache = android.util.LruCache<String, JSONObject>(16)
    @Synchronized fun read(path: String): JSONObject = read(path, false)
    @Synchronized fun additional(path: String): JSONObject = read(path, true)

    private fun read(path: String, additional: Boolean): JSONObject {
        val key = "$additional:$path"
        cache.get(key)?.let { return it }
        val result = JSONObject()
        for ((part, index) in indexes) {
            if (additional && part in setOf("data", "controls", "art")) continue
            val range = index.optJSONArray(path) ?: continue
            val offset = range.getLong(0)
            val size = range.getInt(1)
            require(offset >= 0 && size in 2..(16 * 1024 * 1024))
            val bytes = assets.open("catalog/$part.json").use { input ->
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
            merge(result, JSONObject(bytes.toString(Charsets.UTF_8)))
        }
        cache.put(key, result)
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
}
