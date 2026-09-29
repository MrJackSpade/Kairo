package com.mrjackspade.kairo.frontend

import android.util.AtomicFile
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/** Reads a bounded local catalog without exposing a partially written snapshot. */
object LocalCatalogFile {
    fun read(file: File, maxBytes: Int,
             validRecord: ((String, JSONObject) -> Boolean)? = null): JSONObject? =
        runCatching {
            val root = readObject(file, maxBytes) ?: error("Unreadable local catalog")
            require(root.optInt("schemaVersion") == 1)
            val games = root.optJSONObject("games") ?: error("Missing games")
            if (validRecord != null) for (id in games.keys()) {
                val record = games.optJSONObject(id) ?: error("Invalid game record")
                require(validRecord(id, record)) { "Invalid game record" }
            }
            root
        }.getOrNull()

    fun readObject(file: File, maxBytes: Int): JSONObject? = runCatching {
            require(maxBytes > 0)
            val output = ByteArrayOutputStream()
            AtomicFile(file).openRead().use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= maxBytes) { "Local catalog is too large" }
                    output.write(buffer, 0, count)
                }
            }
            JSONObject(output.toString(Charsets.UTF_8.name()))
        }.getOrNull()
}
