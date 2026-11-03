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
    earlierRecords: Map<Int, (DataInputStream) -> T> = emptyMap()
) {
    data class Snapshot<T>(val treeUri: String, val entries: List<T>, val root: JSONObject)

    /** The file's bytes, apart from where they are stored (Kairo#106). */
    private val format = LibraryCacheFormat(version, writeRecord, readRecord, earlierRecords)

    class Legacy<T>(val file: File, val version: Int, val collectionName: String,
                    val decode: (JSONObject) -> T)

    private fun exists() = file.exists() || File(file.path + ".bak").exists()

    @Synchronized fun read(): Snapshot<T>? {
        if (!exists()) return legacy?.let(::migrate)
        return format.decode(AtomicFile(file).readFully())
    }

    /** Never offer entries from another selected document tree as this library's cache. */
    fun readForTree(treeUri: String): Snapshot<T>? =
        read()?.takeIf { it.treeUri == treeUri }

    @Synchronized fun write(treeUri: String, entries: List<T>, extras: JSONObject = JSONObject()) {
        require(entries.size <= maxEntries) { "Too many library entries" }
        writtenVersion()?.let { written ->
            require(written <= version) { "Library cache uses a newer layout; update the app before scanning" }
        }
        val bytes = format.encode(treeUri, entries, extras)
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
                return if (input.readInt() == LibraryCacheFormat.MAGIC) input.readInt() else null
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

}

/**
 * A library cache file's bytes (shared/tools/library_cache.py reads the same): big-endian, the
 * magic, the record layout [version], the library's tree URI, a JSON text of extras, the entry
 * count, then each entry's record. A file of a layout in [earlierRecords] is read with its reader;
 * one of any other layout is not read.
 */
class LibraryCacheFormat<T>(
    private val version: Int,
    private val writeRecord: (DataOutputStream, T) -> Unit,
    private val readRecord: (DataInputStream) -> T,
    private val earlierRecords: Map<Int, (DataInputStream) -> T> = emptyMap()
) {
    fun encode(treeUri: String, entries: List<T>, extras: JSONObject): ByteArray {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { output ->
            output.writeInt(MAGIC)
            output.writeInt(version)
            output.writeText(treeUri)
            output.writeText(extras.toString())
            output.writeInt(entries.size)
            entries.forEach { writeRecord(output, it) }
        }
        return buffer.toByteArray()
    }

    /** The cache in [bytes]; null for another file or a layout this app does not read. */
    fun decode(bytes: ByteArray): VersionedLibraryCache.Snapshot<T>? =
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            if (input.readInt() != MAGIC) return null
            val readRecord = input.readInt().let { written ->
                if (written == version) readRecord else earlierRecords[written] ?: return null
            }
            val treeUri = input.readText()
            val extras = JSONObject(input.readText())
            val count = input.readInt()
            val entries = ArrayList<T>(count)
            repeat(count) { entries += readRecord(input) }
            VersionedLibraryCache.Snapshot(treeUri, entries, extras)
        }

    companion object {
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
