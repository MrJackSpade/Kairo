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
 *   which tile it exactly. A record of type 0 or 1 is a file: its offset in the data block, its
 *   packed and unpacked sizes, a checksum, flags, three FILETIMEs when its flags say so, its path
 *   (Windows-1252, backslashes), then the shortcuts made to it ([Shortcut]). The files' data tiles
 *   the data block; identical files share one copy.
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
     * installs into), unpacked [size], the installer's own [checksum] of it, and the [shortcuts] the
     * installer makes to it (how the game's setup is started).
     */
    class Member internal constructor(val name: String, val size: Long, val checksum: Long,
                                      internal val offset: Long, internal val packedSize: Long,
                                      val shortcuts: List<Shortcut> = emptyList(),
                                      /** Its record's index in the file list, counting every record. */
                                      internal val index: Int = -1,
                                      /** A patched file's (a type 1 record) reference: the index of the record it patches. */
                                      internal val reference: Int? = null)

    /** A shortcut the installer makes (Start menu or desktop): its [name] (`X.lnk`) and the [arguments] it runs its file with. */
    class Shortcut(val name: String, val arguments: String)

    /** One file of the installer, the program first, then its parts in order; this closes [closeable]. */
    class Part(val channel: FileChannel, val closeable: Closeable)

    /** The installer needs its part [index] (`.D01` is 1), which was not given. */
    class MissingPart(val index: Int) : IOException("The installer's part $index is missing")

    /** The SHA-256 of the installer's file list: the same files, sizes, checksums and offsets, the same digest. */
    fun contentDigest(): ByteArray = MessageDigest.getInstance("SHA-256").digest(fileList)

    /** Reads [member] unpacked; reading it to its end fails if it is not exactly its size or its stream's check fails. */
    fun open(member: Member): InputStream {
        member.reference?.let { reference -> return java.io.ByteArrayInputStream(patched(member, reference)) }
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

    private val byIndex by lazy { members.associateBy { it.index } }

    /**
     * A patched file (a type 1 record): Clickteam's patch against the file of record [reference],
     * as ScummVM's common/compression/clickteam.cpp applies it. Its data is 4 bytes, an operations
     * block and a literals block ([patchBlock]). Each operation byte b: with bit 7, (b & 0x1f) + 1
     * literals when bits 5-6 are 0, else that many (bits 5-6) literals and a copy of (b & 0x1f) + 2;
     * 0, a copy of u16 + 0x81; else a copy of b + 1. A copy's offset follows ([copyDelta]); it reads
     * the reference from base + delta, and base moves past it. Every literal is used, the result is
     * exactly the file's size, and its checksum ([checksum]) is its record's.
     */
    private fun patched(member: Member, reference: Int): ByteArray {
        val original = byIndex[reference] ?: throw IOException("${member.name}: its reference record $reference is not a file")
        val referenceBytes = open(original).use { it.readBytes() }
        val raw = source.read(dataStart + member.offset, Math.toIntExact(member.packedSize + 4))
        val (operations, afterOperations) = patchBlock(raw, 4, member.name)
        val (literals, end) = patchBlock(raw, afterOperations, member.name)
        if (end != raw.size) throw IOException("${member.name}: ${raw.size - end} bytes after its patch")
        val size = Math.toIntExact(member.size)
        val out = ByteArray(size)
        var written = 0
        var at = 0
        var literal = 0
        var base = 0L
        fun put(bytes: ByteArray, from: Int, count: Int) {
            if (written + count > size) throw IOException("${member.name}: its patch makes more than its $size bytes")
            System.arraycopy(bytes, from, out, written, count)
            written += count
        }
        while (at < operations.size) {
            val b = operations[at++].toInt() and 0xFF
            var length = 0
            if (b and 0x80 != 0) {
                val run = (b shr 5) and 3
                val count = if (run == 0) (b and 0x1F) + 1 else run
                if (run != 0) length = (b and 0x1F) + 2
                if (literal + count > literals.size) throw IOException("${member.name}: its patch uses more literals than it holds")
                put(literals, literal, count)
                literal += count
            } else if (b == 0) {
                length = ((operations[at].toInt() and 0xFF) or ((operations[at + 1].toInt() and 0xFF) shl 8)) + 0x81
                at += 2
            } else length = b + 1
            if (length > 0) {
                val (delta, next) = copyDelta(operations, at)
                at = next
                val from = base + delta
                if (from < 0 || from + length > referenceBytes.size)
                    throw IOException("${member.name}: a patch copy of $length at $from leaves its ${referenceBytes.size}-byte reference")
                put(referenceBytes, from.toInt(), length)
                base = from + length
            }
        }
        if (literal != literals.size) throw IOException("${member.name}: ${literals.size - literal} patch literals unused")
        if (written != size) throw IOException("${member.name}: its patch made $written bytes, not $size")
        if (checksum(out) != member.checksum) throw IOException("${member.name}: the patched file's checksum is not its record's")
        return out
    }

    /**
     * A patch block at [at] of [raw]: u8 codec (bit 0 packed, bit 1 a u32 unpacked size else u16,
     * bit 2 a u32 packed size else u16), the unpacked size, the packed size when packed, then a
     * method byte and its stream (packed) or the bytes. Returns the bytes and where the block ends.
     */
    private fun patchBlock(raw: ByteArray, at: Int, name: String): Pair<ByteArray, Int> {
        val buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        val codec = raw[at].toInt() and 0xFF
        if (codec > 7 || codec and 5 == 4) throw IOException("$name: unknown patch block codec $codec")
        var position = at + 1
        val unpacked = if (codec and 2 != 0) buffer.getInt(position).also { position += 4 }
            else (buffer.getShort(position).toInt() and 0xFFFF).also { position += 2 }
        val packed = if (codec and 1 == 0) unpacked
            else if (codec and 4 != 0) buffer.getInt(position).also { position += 4 }
            else (buffer.getShort(position).toInt() and 0xFFFF).also { position += 2 }
        if (unpacked < 0 || packed < 0 || position + packed > raw.size) throw IOException("$name: a patch block runs past its data")
        if (codec and 1 == 0) return raw.copyOfRange(position, position + packed) to position + packed
        val stream = raw.inputStream(position + 1, packed - 1)
        val input = when (val method = raw[position].toInt() and 0xFF) {
            ZLIB -> InflaterInputStream(stream, Inflater())
            BZIP2 -> BZip2CompressorInputStream(stream, false)
            else -> throw IOException("$name: unknown patch block method $method")
        }
        val bytes = SizedStream(input, unpacked.toLong(), name).use { it.readBytes() }
        return bytes to position + packed
    }

    /** A patch copy's offset delta at [at] of [operations], and where it ends. */
    private fun copyDelta(operations: ByteArray, at: Int): Pair<Long, Int> {
        fun byte(index: Int) = operations[index].toInt() and 0xFF
        fun signed(value: Int, bits: Int) = if (value and (1 shl (bits - 1)) != 0) value - (1 shl bits) else value
        fun away(value: Int, amount: Int) = if (value >= 0) value + amount else value - amount
        val c = byte(at)
        return when {
            c and 0xC0 == 0x80 -> signed(c and 0x3F, 6).toLong() to at + 1
            c and 0xC0 == 0xC0 -> {
                val value = ((c and 0x3F) shl 16) or (byte(at + 1) shl 8) or byte(at + 2)
                if (value == 0x100000) ByteBuffer.wrap(operations, at + 3, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() to at + 7
                else away(signed(value, 22), 0x4020).toLong() to at + 3
            }
            else -> away(signed(((c and 0x7F) shl 8) or byte(at + 1), 15), 0x20).toLong() to at + 2
        }
    }

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
     * fields; the rest are a file record's fields, from the record's start. Its path starts at
     * [nameAt] when its [flags] have [TIMES] (three FILETIMEs before it), [TIMES_SIZE] sooner otherwise.
     */
    private enum class Layout(val header: Int, val offset: Int, val packed: Int, val size: Int, val checksum: Int,
                              val flags: Int, val nameAt: Int, val reference: Int?) {
        /** The current one: a u16 size and u16 type; a patched file's reference record is the u32 at 40. */
        CURRENT(4, 6, 10, 18, 22, 26, 68, 40),
        /** Older installers': a u32 size and u32 type. No installer of this layout patches a file, so where its reference is is not known. */
        EARLIER(8, 28, 32, 24, 20, 8, 64, null);

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
        /** Record types that are files; type 2 is the uninstaller's shortcut. */
        private val FILE_TYPES = setOf(0, 1)
        /** A type 1 record's file is a patch against another file of the list ([patched]). */
        private const val PATCHED = 1
        /** A file record's flag: three FILETIMEs come before its path. */
        private const val TIMES = 0x08
        private const val TIMES_SIZE = 24

        /** A record's checksum of its file: per byte, rotate left by 1, then add the byte. */
        fun checksum(bytes: ByteArray): Long {
            var value = 0
            for (byte in bytes) value = Integer.rotateLeft(value, 1) + (byte.toInt() and 0xFF)
            return value.toLong() and 0xFFFFFFFFL
        }

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
                val type = layout.recordType(buffer, position)
                if (type in FILE_TYPES) {
                    val timed = list[position + layout.flags].toInt() and TIMES != 0
                    val nameAt = position + layout.nameAt - if (timed) 0 else TIMES_SIZE
                    require(nameAt < position + size)
                    val end = (nameAt until position + size).firstOrNull { list[it] == 0.toByte() }
                    requireNotNull(end)
                    val name = DiskArchive.safeName(String(list, nameAt, end - nameAt, NAMES))
                    require(names.add(name.lowercase(Locale.ROOT)))
                    members += Member(name, buffer.getInt(position + layout.size).toLong() and 0xFFFFFFFFL,
                        buffer.getInt(position + layout.checksum).toLong() and 0xFFFFFFFFL,
                        buffer.getInt(position + layout.offset).toLong() and 0xFFFFFFFFL,
                        buffer.getInt(position + layout.packed).toLong() and 0xFFFFFFFFL,
                        shortcuts(list, end + 1, position + size), index = records,
                        reference = if (type != PATCHED) null else layout.reference?.let { buffer.getInt(position + it) }
                            ?: throw IOException("$name: a patched file in an earlier installer, whose reference is not known"))
                }
                position += size
                records++
            }
            require(position == list.size && records == count)
            // The files' data tiles the data block: each starts where the one before it ends, or
            // shares its bytes (one copy of identical files). A misread record breaks this.
            val byOffset = members.sortedBy { it.offset }
            for (index in 1 until byOffset.size) {
                val before = byOffset[index - 1]
                val member = byOffset[index]
                require(member.offset == before.offset || member.offset == before.offset + before.packedSize)
            }
            return members
        }

        /**
         * The shortcuts the installer makes to a file, from the strings after its path
         * ([start] to [end]): its Start menu group, then a Start menu and a desktop shortcut, each
         * its name, arguments and icon; an empty name is no shortcut.
         */
        private fun shortcuts(list: ByteArray, start: Int, end: Int): List<Shortcut> {
            val fields = ArrayList<String>()
            var at = start
            while (at < end) {
                val stop = (at until end).firstOrNull { list[it] == 0.toByte() } ?: end
                fields += String(list, at, stop - at, NAMES)
                at = stop + 1
            }
            return listOf(1, 4).mapNotNull { index ->
                fields.getOrNull(index)?.takeIf { it.isNotEmpty() }?.let { Shortcut(it, fields.getOrNull(index + 1).orEmpty()) }
            }
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
