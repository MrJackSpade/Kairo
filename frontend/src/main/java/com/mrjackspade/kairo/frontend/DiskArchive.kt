package com.mrjackspade.kairo.frontend

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.Closeable
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.FileChannel
import java.util.Locale
import java.util.zip.CRC32

/**
 * A ZIP or 7z archive of the games folder, read in place from its document: the document's file
 * descriptor is seekable, so the readers fetch only the bytes they need and nothing is copied. Every
 * file in it is a [Member], by its path in the archive; a path that could reach outside a folder the
 * archive is unpacked into, or two members of one path, fail the archive. The disk library reads its
 * images through it (DiskImageLibrary), and an app that unpacks a disk where the machine runs it
 * (KairoX86) reads the same way.
 */
class DiskArchive private constructor(private val context: Context, private val document: ParcelFileDescriptor,
                                      private val reader: Reader) : Closeable {
    /** A file of the archive: its path in it (as the archive spells it), size, and CRC-32 when the archive records one. */
    class Member(val name: String, val size: Long, val crc: Long)

    /** Every file of the archive, in the archive's order. */
    val members: List<Member> = reader.members

    private val byName: Map<String, Member> = members.associateBy { it.name }

    /** Reads [member]. */
    fun open(member: Member): InputStream = reader.open(member.name)

    /** Reads the file at [name], its path in the archive. */
    fun open(name: String): InputStream =
        reader.open(byName[name]?.name ?: error(context.getString(R.string.kairo_disk_member_missing, name)))

    /**
     * Copies [member] to [output] whole, checking its size and, when the archive records one, its
     * CRC-32; [checkCancelled] runs between blocks.
     */
    fun copy(member: Member, output: OutputStream, checkCancelled: () -> Unit) = open(member).use { input ->
        val check = CRC32()
        val buffer = ByteArray(64 * 1024)
        var count = 0L
        while (true) {
            checkCancelled()
            val read = input.read(buffer)
            if (read < 0) break
            count += read
            check.update(buffer, 0, read)
            output.write(buffer, 0, read)
        }
        require(member.size < 0 || count == member.size) { context.getString(R.string.kairo_disk_image_size_changed) }
        require(member.crc < 0 || check.value == member.crc) { context.getString(R.string.kairo_disk_archive_checksum) }
    }

    override fun close() {
        try { reader.close() } finally { document.close() }
    }

    /** One archive format's reader. */
    private interface Reader : Closeable {
        val members: List<Member>
        fun open(name: String): InputStream
    }

    private class ZipReader(channel: FileChannel, checked: Checked) : Reader {
        private val zip = ZipFile.builder().setSeekableByteChannel(channel).get()
        private val entries = HashMap<String, ZipArchiveEntry>()
        override val members: List<Member> = try {
            zip.entries.asSequence().mapNotNull { item ->
                checked.add(item.name)
                if (item.isDirectory) null else Member(item.name, item.size, item.crc).also { entries[item.name] = item }
            }.toList()
        } catch (error: Exception) {
            zip.close()
            throw error
        }

        override fun open(name: String): InputStream = zip.getInputStream(entries.getValue(name))
        override fun close() = zip.close()
    }

    private class SevenZReader(channel: FileChannel, checked: Checked) : Reader {
        private val archive = SevenZFile.builder().setSeekableByteChannel(channel).get()
        private val entries = HashMap<String, SevenZArchiveEntry>()
        override val members: List<Member> = try {
            archive.entries.mapNotNull { item ->
                checked.add(item.name)
                if (item.isDirectory || !item.hasStream()) null
                else Member(item.name, item.size, if (item.hasCrc) item.crcValue else -1).also { entries[item.name] = item }
            }
        } catch (error: Exception) {
            archive.close()
            throw error
        }

        override fun open(name: String): InputStream = archive.getInputStream(entries.getValue(name))
        override fun close() = archive.close()
    }

    /**
     * Checks each entry's path as the archive lists it: at most [MAX_ENTRIES] of them, each safe
     * ([safeName]) and none spelled as another is, ignoring case.
     */
    private class Checked(private val context: Context) {
        private val names = HashSet<String>()
        private var count = 0

        fun add(name: String) {
            require(++count <= MAX_ENTRIES) { context.getString(R.string.kairo_disk_archive_too_many_entries) }
            require(names.add(safeName(name).lowercase(Locale.ROOT))) { context.getString(R.string.kairo_disk_archive_duplicate) }
        }
    }

    companion object {
        const val MAX_ENTRIES = 50_000
        const val MAX_BYTES = 8L * 1024 * 1024 * 1024

        /** Whether a file named [name] is an archive this reads: ZIP or 7z, by extension. */
        fun isArchive(name: String): Boolean = name.substringAfterLast('.', "").lowercase(Locale.ROOT) in ARCHIVES

        private val ARCHIVES = setOf("zip", "7z")

        /**
         * Opens the archive [name] at [uri] where it is. [size] is its size when the library listed
         * it (-1 when unknown): an archive whose size has changed since fails, as it was rewritten.
         */
        fun open(context: Context, uri: Uri, name: String, size: Long): DiskArchive {
            require(size <= MAX_BYTES) { context.getString(R.string.kairo_disk_archive_too_large) }
            val document = context.contentResolver.openFileDescriptor(uri, "r") ?: error("Unable to open archive")
            try {
                if (size >= 0) require(document.statSize == size) { context.getString(R.string.kairo_disk_archive_changed) }
                require(document.statSize <= MAX_BYTES) { context.getString(R.string.kairo_disk_archive_too_large) }
                val channel = FileInputStream(document.fileDescriptor).channel
                // A provider that streams the document through a pipe cannot be read in place.
                require(document.statSize >= 0 && runCatching { channel.position(0) }.isSuccess) {
                    context.getString(R.string.kairo_disk_archive_not_seekable)
                }
                val checked = Checked(context)
                val reader = if (name.substringAfterLast('.', "").lowercase(Locale.ROOT) == "7z") SevenZReader(channel, checked)
                    else ZipReader(channel, checked)
                return DiskArchive(context, document, reader)
            } catch (error: Exception) {
                document.close()
                throw error
            }
        }

        /**
         * [name] with forward slashes, as a path relative to a folder it may be unpacked into; fails
         * for one that could leave the folder (absolute, a drive, `..`) or is empty.
         */
        fun safeName(name: String): String {
            val normalized = name.replace('\\', '/').removeSuffix("/")
            require(normalized.isNotBlank() && !normalized.startsWith('/') &&
                normalized.length <= 4096 && normalized.split('/').none(String::isEmpty) &&
                !normalized.contains(':') && !normalized.contains('\u0000') &&
                normalized.split('/').none { it == ".." || it == "." }) { "Unsafe archive entry path" }
            return normalized
        }
    }
}
