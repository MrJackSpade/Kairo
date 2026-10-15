package com.mrjackspade.kairo.frontend

import android.util.AtomicFile
import org.json.JSONObject
import java.io.File

/**
 * Atomic local overrides; preserves either flat or schema-v1 games packaging. Records are not
 * checked on load: one bad record must not hide every other override.
 */
class GameMetadataOverrides(
    private val file: File,
    private val maxBytes: Int = 4 * 1024 * 1024,
    private val schemaEnvelope: Boolean = false
) {
    private val loaded = read()
    private var values = loaded ?: empty()
    private val unreadable = loaded == null && exists()

    @Synchronized fun record(contentId: String): JSONObject? =
        games(values).optJSONObject(contentId)?.let { JSONObject(it.toString()) }

    @Synchronized fun set(contentId: String, field: String, value: Any?) = change { root ->
        val entries = games(root)
        val game = entries.optJSONObject(contentId) ?: JSONObject()
        if (value == null) game.remove(field) else game.put(field, value)
        if (game.length() == 0) entries.remove(contentId) else entries.put(contentId, game)
    }

    @Synchronized fun setSubfield(contentId: String, field: String, subfield: String,
                                  value: Any?) = updateSubfields(contentId, field, mapOf(subfield to value))

    @Synchronized fun updateSubfields(contentId: String, field: String,
                                       changes: Map<String, Any?>) {
        if (changes.isEmpty()) return
        change { root ->
            val entries = games(root)
            val game = entries.optJSONObject(contentId) ?: JSONObject()
            val nested = game.optJSONObject(field) ?: JSONObject()
            for ((subfield, value) in changes) {
                if (value == null) nested.remove(subfield) else nested.put(subfield, value)
            }
            if (nested.length() == 0) game.remove(field) else game.put(field, nested)
            if (game.length() == 0) entries.remove(contentId) else entries.put(contentId, game)
        }
    }

    @Synchronized fun clear(contentId: String, field: String? = null) = change { root ->
        val entries = games(root)
        if (field == null) entries.remove(contentId)
        else entries.optJSONObject(contentId)?.let { game ->
            game.remove(field)
            if (game.length() == 0) entries.remove(contentId)
        }
    }

    private fun change(update: (JSONObject) -> Unit) {
        check(!unreadable && (!exists() || read() != null)) {
            "Existing ${file.name} is unreadable or unsupported; original file was preserved"
        }
        val next = JSONObject(values.toString())
        update(next)
        val bytes = next.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= maxBytes) { "Too many game overrides" }
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            stream.write(bytes)
            atomic.finishWrite(stream)
            val committed = read()
            check(committed != null && committed.toString() == next.toString()) {
                "Game overrides could not be committed"
            }
            values = committed
        } catch (failure: Exception) {
            atomic.failWrite(stream)
            throw failure
        }
    }

    private fun games(root: JSONObject): JSONObject =
        if (schemaEnvelope) root.getJSONObject("games") else root

    /** Whether this is a file layout this class reads, not whether its records are acceptable. */
    private fun supported(root: JSONObject): Boolean =
        !schemaEnvelope || (root.optInt("schemaVersion") == 1 && root.optJSONObject("games") != null)

    private fun empty() = if (schemaEnvelope)
        JSONObject().put("schemaVersion", 1).put("games", JSONObject()) else JSONObject()

    private fun exists() = file.exists() || File(file.path + ".bak").exists()

    private fun read(): JSONObject? = LocalCatalogFile.readObject(file, maxBytes)?.takeIf(::supported)
}
