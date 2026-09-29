package com.mrjackspade.kairo.frontend

import android.util.AtomicFile
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/** Atomic local field overrides keyed by a game's stable content ID. */
class GameMetadataOverrides(private val file: File, private val maxBytes: Int = 4 * 1024 * 1024) {
    private val loaded = read()
    private var values = loaded ?: JSONObject()
    private val unreadable = loaded == null &&
        (file.exists() || File(file.path + ".bak").exists())

    @Synchronized fun record(contentId: String): JSONObject? =
        values.optJSONObject(contentId)?.let { JSONObject(it.toString()) }

    @Synchronized fun set(contentId: String, field: String, value: String?) = change { root ->
        val game = root.optJSONObject(contentId) ?: JSONObject()
        if (value == null) game.remove(field) else game.put(field, value)
        if (game.length() == 0) root.remove(contentId) else root.put(contentId, game)
    }

    @Synchronized fun setSubfield(contentId: String, field: String, subfield: String,
                                  value: String?) = change { root ->
        val game = root.optJSONObject(contentId) ?: JSONObject()
        val nested = game.optJSONObject(field) ?: JSONObject()
        if (value == null) nested.remove(subfield) else nested.put(subfield, value)
        if (nested.length() == 0) game.remove(field) else game.put(field, nested)
        if (game.length() == 0) root.remove(contentId) else root.put(contentId, game)
    }

    @Synchronized fun clear(contentId: String) = change { it.remove(contentId) }

    private fun change(update: (JSONObject) -> Unit) {
        check(!unreadable) { "Game overrides are unreadable; original file was preserved" }
        val next = JSONObject(values.toString())
        update(next)
        val bytes = next.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= maxBytes) { "Too many game overrides" }
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            stream.write(bytes)
            atomic.finishWrite(stream)
            values = next
        } catch (failure: Exception) {
            atomic.failWrite(stream)
            throw failure
        }
    }

    private fun read(): JSONObject? = runCatching {
        val atomic = AtomicFile(file)
        atomic.openRead().use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= maxBytes) { "Too many game overrides" }
                output.write(buffer, 0, count)
            }
            JSONObject(output.toString(Charsets.UTF_8.name()))
        }
    }.getOrNull()
}
