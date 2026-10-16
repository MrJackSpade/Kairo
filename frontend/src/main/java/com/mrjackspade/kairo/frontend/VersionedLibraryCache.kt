package com.mrjackspade.kairo.frontend

import android.util.AtomicFile
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * Bounded, atomic binary library cache with a product-owned record codec. Records are written
 * field by field in a fixed order, so reading needs no parsing. [version] names that record
 * layout. A cache written with an earlier layout is read with its entry in [earlierRecords],
 * so a new field does not cost a rescan; any other layout is not read, and the next scan
 * replaces it.
 */
class VersionedLibraryCache<T>(
    private val file: File,
    private val version: Int,
    private val maxBytes: Int,
    private val maxEntries: Int,
    private val writeRecord: (DataOutputStream, T) -> Unit,
    private val readRecord: (DataInputStream) -> T,
    /** The JSON cache that earlier versions wrote; read once and replaced by a binary cache. */
    private val legacy: Legacy<T>? = null,
    private val earlierRecords: Map<Int, (DataInputStream) -> T> = emptyMap()
) {
    data class Snapshot<T>(val treeUri: String, val entries: List<T>, val root: JSONObject)

    class Legacy<T>(val file: File, val version: Int, val collectionName: String,
                    val decode: (JSONObject) -> T)

    private fun exists() = file.exists() || File(file.path + ".bak").exists()

    @Synchronized fun read(): Snapshot<T>? {
        if (!exists()) return legacy?.let(::migrate)
        DataInputStream(ByteArrayInputStream(AtomicFile(file).readFully())).use { input ->
            if (input.readInt() != MAGIC) return null
            val readRecord = input.readInt().let { written ->
                if (written == version) readRecord else earlierRecords[written] ?: return null
            }
            val treeUri = input.readText()
            val extras = JSONObject(input.readText())
            val count = input.readInt()
            val entries = ArrayList<T>(count)
            repeat(count) { entries += readRecord(input) }
            return Snapshot(treeUri, entries, extras)
        }
    }

    /** Never offer entries from another selected document tree as this library's cache. */
    fun readForTree(treeUri: String): Snapshot<T>? =
        read()?.takeIf { it.treeUri == treeUri }

    @Synchronized fun write(treeUri: String, entries: List<T>, extras: JSONObject = JSONObject()) {
        require(entries.size <= maxEntries) { "Too many library entries" }
        writtenVersion()?.let { written ->
            require(written <= version) { "Library cache uses a newer layout; update the app before scanning" }
        }
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { output ->
            output.writeInt(MAGIC)
            output.writeInt(version)
            output.writeText(treeUri)
            output.writeText(extras.toString())
            output.writeInt(entries.size)
            entries.forEach { writeRecord(output, it) }
        }
        val bytes = buffer.toByteArray()
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
        legacy?.let { AtomicFile(it.file).delete() }
    }

    /** The layout version of the cache on disk, read from its header alone. */
    private fun writtenVersion(): Int? {
        if (!exists()) return null
        AtomicFile(file).openRead().use { stream ->
            DataInputStream(stream).use { input ->
                return if (input.readInt() == MAGIC) input.readInt() else null
            }
        }
    }

    private fun migrate(old: Legacy<T>): Snapshot<T>? {
        val oldFile = AtomicFile(old.file)
        if (!old.file.exists() && !File(old.file.path + ".bak").exists()) return null
        val root = JSONObject(oldFile.readFully().toString(Charsets.UTF_8))
        if (root.optInt("schemaVersion") != old.version) return null
        val values = root.getJSONArray(old.collectionName)
        val entries = (0 until values.length()).map { old.decode(values.getJSONObject(it)) }
        val extras = JSONObject(root.toString()).apply {
            remove("schemaVersion"); remove("treeUri"); remove(old.collectionName)
        }
        val snapshot = Snapshot(root.getString("treeUri"), entries, extras)
        write(snapshot.treeUri, entries, extras)
        return snapshot
    }

    private companion object {
        /** "KLIB": a Kairo binary library cache. */
        const val MAGIC = 0x4B4C4942
    }
}

/** Record fields for [VersionedLibraryCache] codecs. Lengths are not limited to 64 KiB. */
fun DataOutputStream.writeText(value: String) {
    val bytes = value.toByteArray(Charsets.UTF_8)
    writeInt(bytes.size)
    write(bytes)
}

fun DataInputStream.readText(): String {
    val bytes = ByteArray(readInt())
    readFully(bytes)
    return String(bytes, Charsets.UTF_8)
}

fun DataOutputStream.writeOptionalText(value: String?) {
    writeBoolean(value != null)
    if (value != null) writeText(value)
}

fun DataInputStream.readOptionalText(): String? = if (readBoolean()) readText() else null
