package com.mrjackspade.kairo.frontend

import android.util.AtomicFile
import org.json.JSONObject
import java.io.File

/**
 * Atomic local overrides, one record per game by its [GameKey.path]; preserves either flat or
 * schema-v1 games packaging. Records are not checked on load: one bad record must not hide every
 * other override.
 */
class GameMetadataOverrides(
    private val file: File,
    private val maxBytes: Int = 4 * 1024 * 1024,
    private val schemaEnvelope: Boolean = false
) {
    private val loaded = read()
    private var values = loaded ?: empty()
    private val unreadable = loaded == null && exists()

    @Synchronized fun record(game: GameKey): JSONObject? =
        games(values).optJSONObject(game.path)?.let { JSONObject(it.toString()) }

    @Synchronized fun set(game: GameKey, field: String, value: Any?) = change { root ->
        val entries = games(root)
        val id = game.path
        val record = entries.optJSONObject(id) ?: JSONObject()
        if (value == null) record.remove(field) else record.put(field, value)
        if (record.length() == 0) entries.remove(id) else entries.put(id, record)
    }

    @Synchronized fun setSubfield(game: GameKey, field: String, subfield: String,
                                  value: Any?) = updateSubfields(game, field, mapOf(subfield to value))

    @Synchronized fun updateSubfields(game: GameKey, field: String,
                                       changes: Map<String, Any?>) {
        if (changes.isEmpty()) return
        change { root ->
            val entries = games(root)
            val id = game.path
            val record = entries.optJSONObject(id) ?: JSONObject()
            val nested = record.optJSONObject(field) ?: JSONObject()
            for ((subfield, value) in changes) {
                if (value == null) nested.remove(subfield) else nested.put(subfield, value)
            }
            if (nested.length() == 0) record.remove(field) else record.put(field, nested)
            if (record.length() == 0) entries.remove(id) else entries.put(id, record)
        }
    }

    @Synchronized fun clear(game: GameKey, field: String? = null) = change { root ->
        val entries = games(root)
        val id = game.path
        if (field == null) entries.remove(id)
        else entries.optJSONObject(id)?.let { record ->
            record.remove(field)
            if (record.length() == 0) entries.remove(id)
        }
    }

    /** Records from before games had keys, named by content ID. */
    @Synchronized fun contentIdRecords(): Set<String> =
        games(values).keys().asSequence().filter { CONTENT_ID.matches(it) }.toSet()

    /** Records v1.4.0 and earlier named by a game's [GameKey.hashName]. */
    @Synchronized fun v1Records(): Set<String> =
        games(values).keys().asSequence().filter { GameKey.HASH_NAME.matches(it) }.toSet()

    /** True when v1.4.0 or earlier kept a record under [hashName]. */
    @Synchronized fun hasV1Record(hashName: String): Boolean = games(values).optJSONObject(hashName) != null

    /**
     * Moves the record v1.4.0 and earlier kept under [hashName] to [to], in one write. A record [to]
     * already has is kept, and the earlier one is dropped.
     */
    @Synchronized fun moveV1Record(hashName: String, to: GameKey) = change { root ->
        val entries = games(root)
        val record = entries.optJSONObject(hashName) ?: return@change
        if (entries.optJSONObject(to.path) == null) entries.put(to.path, record)
        entries.remove(hashName)
    }

    /** Gives each of [games] a copy of [contentId]'s record, then removes that record, in one write. */
    @Synchronized fun moveContentIdRecord(contentId: String, games: Collection<GameKey>) = change { root ->
        val entries = games(root)
        val record = entries.optJSONObject(contentId) ?: return@change
        for (game in games) entries.put(game.path, JSONObject(record.toString()))
        entries.remove(contentId)
    }

    /** Moves a game's record to another key, as when an installer becomes its installed game. */
    @Synchronized fun move(from: GameKey, to: GameKey) = change { root ->
        val entries = games(root)
        val record = entries.optJSONObject(from.path) ?: return@change
        entries.put(to.path, record)
        entries.remove(from.path)
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

    private companion object {
        val CONTENT_ID = Regex("sha256-[a-z0-9-]+-v1:[0-9a-f]+")
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
