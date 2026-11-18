package com.mrjackspade.kairo.frontend

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.charset.Charset
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

/**
 * A Clickteam Install Creator installer, the form The Collection Chamber ships its games in: a
 * Windows program (`Install-XXX.exe`) carrying the game's files, spanned over parts beside it
 * (`Install-XXX.D01`, `.D02`, ...) when it is large. Read in place, from the channels of the
 * program and its parts: a [Member] is read by decompressing only its own bytes, and nothing runs.
 *
 * The format has no published description; this is its layout as the installers have it.
 * - The program's overlay (the bytes after its last PE section) starts with [SIGNATURE], then
 *   blocks, each a u16 ID, u16 flags and u32 size before its bytes. All numbers are little-endian.
 * - Every block but the data block holds its unpacked size (u32), a [method](unpack) byte and the
 *   packed bytes. [FILE_LIST] lists the files; [DATA], always last, holds them, and runs on from
 *   the end of the program through each part in order. A part starts with 4 bytes that are not data.
 * - The file list is its record count (u32), then the records, in one of two layouts ([Layout]),
 *   which tile it exactly. A record of type 0 is a file: its offset in the data block, its packed
 *   and unpacked sizes, a checksum and its path (Windows-1252, backslashes).
 * - At a file's offset: 4 bytes (the previous stream's Adler-32 trailer), the method byte, then the
 *   packed stream, which ends 4 bytes past offset + packed size (its own trailer, read by the next
 *   record's first 4 bytes, or the data block's last 4). An empty file is its method byte alone
 *   (packed size 1): no stream follows it.
 */
class ClickteamInstaller private constructor(
    private val source: Source,
    /** Every file the installer installs, in its order. */
    val members: List<Member>,
    /** The file list as the installer holds it, unpacked: what [contentDigest] hashes. */
    private val fileList: ByteArray,
    private val dataStart: Long
) : Closeable {
    /**
     * A file the installer installs: its [name] (forward slashes, a path relative to the folder it
     * installs into), unpacked [size] and the installer's own [checksum] of it.
     */
    class Member internal constructor(val name: String, val size: Long, val checksum: Long,
                                      internal val offset: Long, internal val packedSize: Long)

    /** One file of the installer, the program first, then its parts in order; this closes [closeable]. */
    class Part(val channel: FileChannel, val closeable: Closeable)

    /** The installer needs its part [index] (`.D01` is 1), which was not given. */
    class MissingPart(val index: Int) : IOException("The installer's part $index is missing")

    /** The SHA-256 of the installer's file list: the same files, sizes, checksums and offsets, the same digest. */
    fun contentDigest(): ByteArray = MessageDigest.getInstance("SHA-256").digest(fileList)

    /** Reads [member] unpacked; reading it to its end fails if it is not exactly its size or its stream's check fails. */
    fun open(member: Member): InputStream {
        // An empty file is its method byte alone: no stream follows.
        if (member.packedSize <= 1) {
            if (member.size != 0L) throw IOException("${member.name}: ${member.size} bytes with no stream")
            return java.io.ByteArrayInputStream(ByteArray(0))
        }
        val start = dataStart + member.offset
        val method = source.read(start + 4, 1)[0].toInt() and 0xFF
        // Buffered: the decoders read a byte at a time, and each read of the source is a positional read of its file.
        val packed = RangeStream(source, start + 5, start + member.packedSize + 4).buffered(64 * 1024)
        val unpacked: InputStream = when (method) {
            ZLIB -> InflaterInputStream(packed, Inflater(), 64 * 1024)
            BZIP2 -> BZip2CompressorInputStream(packed, false)
            else -> throw IOException("${member.name}: unknown compression method $method")
        }
        return SizedStream(unpacked, member.size, member.name)
    }

    override fun close() = source.close()

    /** The program and its parts as one stream of the overlay's bytes, read by position. */
    private class Source(private val parts: List<Part>) : Closeable {
        /** Each part's first logical position, with the channel position it starts at. */
        private val starts = LongArray(parts.size)
        private val skips = LongArray(parts.size) { if (it == 0) 0 else PART_HEADER }
        val size: Long

        init {
            var at = 0L
            for (index in parts.indices) {
                starts[index] = at
                val length = parts[index].channel.size() - skips[index]
                if (length < 0) throw IOException("The installer's part $index is too short")
                at += length
            }
            size = at
        }

        fun read(position: Long, count: Int): ByteArray {
            val out = ByteArray(count)
            read(position, out, 0, count)
            return out
        }

        fun read(position: Long, into: ByteArray, offset: Int, count: Int) {
            var done = 0
            while (done < count) {
                val at = position + done
                if (at >= size) throw MissingPart(parts.size)
                var index = starts.size - 1
                while (starts[index] > at) index--
                val inPart = at - starts[index]
                val channel = parts[index].channel
                val available = channel.size() - skips[index] - inPart
                val want = minOf(count - done.toLong(), available).toInt()
                val buffer = ByteBuffer.wrap(into, offset + done, want)
                var position0 = skips[index] + inPart
                while (buffer.hasRemaining()) {
                    val read = channel.read(buffer, position0)
                    if (read < 0) throw EOFException("The installer's part $index ended early")
                    position0 += read
                }
                done += want
            }
        }

        override fun close() {
            var failure: Throwable? = null
            for (part in parts) try { part.closeable.close() } catch (error: Throwable) {
                if (failure == null) failure = error else failure.addSuppressed(error)
            }
            failure?.let { throw it }
        }
    }

    /** The bytes of [source] from [start] to [end]. */
    private class RangeStream(private val source: Source, start: Long, private val end: Long) : InputStream() {
        private var position = start

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            if (position >= end) return -1
            val count = minOf(length.toLong(), end - position).toInt()
            source.read(position, buffer, offset, count)
            position += count
            return count
        }
    }

    /** [input], which must be exactly [size] bytes: reading past its end, or ending short, fails. */
    private class SizedStream(private val input: InputStream, private val size: Long, private val name: String) : InputStream() {
        private var count = 0L

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val read = input.read(buffer, offset, length)
            if (read < 0) {
                if (count != size) throw IOException("$name: unpacked to $count bytes, not its $size")
                return -1
            }
            count += read
            if (count > size) throw IOException("$name: unpacks to more than its $size bytes")
            return read
        }

        override fun close() = input.close()
    }

    /**
     * The two record layouts of the file list. [header] is the size of the record's size and type
     * fields; the rest are a file record's fields, from the record's start.
     */
    private enum class Layout(val header: Int, val offset: Int, val packed: Int, val size: Int, val checksum: Int, val nameAt: Int) {
        /** The current one: a u16 size and u16 type. */
        CURRENT(4, 6, 10, 18, 22, 68),
        /** Older installers': a u32 size and u32 type. */
        EARLIER(8, 28, 32, 24, 20, 64);

        fun recordSize(list: ByteBuffer, at: Int) =
            if (header == 4) list.getShort(at).toInt() and 0xFFFF else list.getInt(at)

        fun recordType(list: ByteBuffer, at: Int) =
            if (header == 4) list.getShort(at + 2).toInt() and 0xFFFF else list.getInt(at + 4)
    }

    companion object {
        val SIGNATURE = byteArrayOf(0x77, 0x77, 0x67, 0x54, 0x29, 0x48)
        const val FILE_LIST = 0x143A
        const val DATA = 0x7F7F
        private const val ZLIB = 1
        private const val BZIP2 = 2
        private const val PART_HEADER = 4L
        /** Blocks before the data block hold settings, pictures and lists: never this large. */
        private const val MAX_BLOCK = 64 * 1024 * 1024
        private val NAMES: Charset = Charset.forName("windows-1252")

        /** The name of part [index] (1 for `.D01`) of the installer program [program]: its name with `.Dnn`. */
        fun partName(program: String, index: Int): String {
            require(index in 1..99) { "Part $index is outside .D01 to .D99" }
            return program.substringBeforeLast('.') + ".D" + String.format(Locale.ROOT, "%02d", index)
        }

        /** Whether [program] is a Clickteam installer: a PE program whose overlay starts with [SIGNATURE]. */
        fun isInstaller(program: FileChannel): Boolean {
            val overlay = overlayOffset(program) ?: return false
            if (overlay + SIGNATURE.size > program.size()) return false
            return readFully(program, overlay, SIGNATURE.size).contentEquals(SIGNATURE)
        }

        /**
         * Opens the installer whose program is [parts]' first and whose parts (`.D01` on) follow in
         * order. It owns [parts] from here, closing them when it closes or fails to open.
         * A part the files need that is not given fails with [MissingPart].
         */
        fun open(parts: List<Part>): ClickteamInstaller {
            require(parts.isNotEmpty()) { "No installer program" }
            val source = Source(parts)
            try {
                val program = parts[0].channel
                val overlay = overlayOffset(program) ?: throw IOException("Not a PE program")
                if (!source.read(overlay, SIGNATURE.size).contentEquals(SIGNATURE)) throw IOException("Not a Clickteam installer")
                var position = overlay + SIGNATURE.size
                var fileList: ByteArray? = null
                while (true) {
                    val header = ByteBuffer.wrap(source.read(position, 8)).order(ByteOrder.LITTLE_ENDIAN)
                    val id = header.getShort(0).toInt() and 0xFFFF
                    val size = header.getInt(4).toLong() and 0xFFFFFFFFL
                    position += 8
                    if (id == DATA) break
                    if (size > MAX_BLOCK) throw IOException("Installer block ${"%04x".format(id)} is $size bytes")
                    if (id == FILE_LIST) fileList = unpackBlock(source.read(position, size.toInt()))
                    position += size
                }
                val list = fileList ?: throw IOException("The installer has no file list")
                val members = members(list)
                val needed = members.maxOfOrNull { position + it.offset + it.packedSize + 4 } ?: position
                if (needed > source.size) throw MissingPart(parts.size)
                return ClickteamInstaller(source, members, list, position)
            } catch (error: Throwable) {
                try { source.close() } catch (closing: Throwable) { error.addSuppressed(closing) }
                throw error
            }
        }

        /** A block's bytes unpacked: its unpacked size, method and packed bytes. */
        private fun unpackBlock(block: ByteArray): ByteArray {
            if (block.size < 5) throw IOException("Installer block too short")
            val size = ByteBuffer.wrap(block).order(ByteOrder.LITTLE_ENDIAN).getInt(0)
            if (size < 0 || size > MAX_BLOCK) throw IOException("Installer block unpacks to $size bytes")
            val packed = block.inputStream(5, block.size - 5)
            val input = when (val method = block[4].toInt() and 0xFF) {
                ZLIB -> InflaterInputStream(packed, Inflater())
                BZIP2 -> BZip2CompressorInputStream(packed, false)
                else -> throw IOException("Installer block uses unknown compression method $method")
            }
            val out = ByteArray(size)
            input.use {
                var done = 0
                while (done < size) {
                    val read = it.read(out, done, size - done)
                    if (read < 0) throw IOException("Installer block unpacked short")
                    done += read
                }
                if (it.read() >= 0) throw IOException("Installer block unpacks to more than its size")
            }
            return out
        }

        /** The files of [list]: parsed in the one layout its records tile it in; any other list fails. */
        private fun members(list: ByteArray): List<Member> {
            val attempts = Layout.entries.map { layout -> layout to runCatching { parse(list, layout) } }
            val fits = attempts.mapNotNull { it.second.getOrNull() }
            fits.singleOrNull()?.let { return it }
            throw IOException(if (fits.isEmpty()) "Unrecognised installer file list" else "Ambiguous installer file list").apply {
                attempts.forEach { (layout, result) -> result.exceptionOrNull()?.let { addSuppressed(IOException("As $layout", it)) } }
            }
        }

        /** The files of [list] in [layout]; fails unless its records tile the list exactly and number its count. */
        private fun parse(list: ByteArray, layout: Layout): List<Member> {
            val buffer = ByteBuffer.wrap(list).order(ByteOrder.LITTLE_ENDIAN)
            val count = buffer.getInt(0)
            val members = ArrayList<Member>()
            val names = HashSet<String>()
            var position = 4
            var records = 0
            while (position < list.size) {
                require(position + layout.header <= list.size)
                val size = layout.recordSize(buffer, position)
                require(size >= layout.header && size <= list.size - position)
                if (layout.recordType(buffer, position) == 0) {
                    require(size > layout.nameAt)
                    val end = (position + layout.nameAt until position + size).firstOrNull { list[it] == 0.toByte() }
                    requireNotNull(end)
                    val name = DiskArchive.safeName(String(list, position + layout.nameAt, end - position - layout.nameAt, NAMES))
                    require(names.add(name.lowercase(Locale.ROOT)))
                    members += Member(name, buffer.getInt(position + layout.size).toLong() and 0xFFFFFFFFL,
                        buffer.getInt(position + layout.checksum).toLong() and 0xFFFFFFFFL,
                        buffer.getInt(position + layout.offset).toLong() and 0xFFFFFFFFL,
                        buffer.getInt(position + layout.packed).toLong() and 0xFFFFFFFFL)
                }
                position += size
                records++
            }
            require(position == list.size && records == count)
            return members
        }

        /** Where [program]'s overlay starts: the end of its furthest PE section; null when it is not a PE program. */
        private fun overlayOffset(program: FileChannel): Long? {
            if (program.size() < 0x40) return null
            val dos = ByteBuffer.wrap(readFully(program, 0, 0x40)).order(ByteOrder.LITTLE_ENDIAN)
            if (dos.getShort(0) != 0x5A4D.toShort()) return null
            val pe = dos.getInt(0x3C).toLong() and 0xFFFFFFFFL
            if (pe + 24 > program.size()) return null
            val header = ByteBuffer.wrap(readFully(program, pe, 24)).order(ByteOrder.LITTLE_ENDIAN)
            if (header.getInt(0) != 0x00004550) return null
            val sections = header.getShort(6).toInt() and 0xFFFF
            val optional = header.getShort(20).toInt() and 0xFFFF
            val table = pe + 24 + optional
            if (table + 40L * sections > program.size()) return null
            val entries = ByteBuffer.wrap(readFully(program, table, 40 * sections)).order(ByteOrder.LITTLE_ENDIAN)
            var end = 0L
            for (index in 0 until sections) {
                val rawSize = entries.getInt(40 * index + 16).toLong() and 0xFFFFFFFFL
                val rawPointer = entries.getInt(40 * index + 20).toLong() and 0xFFFFFFFFL
                end = maxOf(end, rawPointer + rawSize)
            }
            return end.takeIf { sections > 0 }
        }

        private fun readFully(channel: FileChannel, position: Long, count: Int): ByteArray {
            val buffer = ByteBuffer.allocate(count)
            var at = position
            while (buffer.hasRemaining()) {
                val read = channel.read(buffer, at)
                if (read < 0) throw EOFException("Unexpected end of file")
                at += read
            }
            return buffer.array()
        }
    }
}
