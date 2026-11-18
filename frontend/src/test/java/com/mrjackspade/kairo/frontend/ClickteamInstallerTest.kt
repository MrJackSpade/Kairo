package com.mrjackspade.kairo.frontend

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.util.zip.DeflaterOutputStream
import kotlin.random.Random

class ClickteamInstallerTest {
    @get:Rule val folder = TemporaryFolder()

    private val files = linkedMapOf(
        "HDD\\GAME\\GAME.EXE" to Random(1).nextBytes(70_000),
        "dosbox.conf" to "[autoexec]\r\nmount C \"HDD\"\r\nC:\r\ncd GAME\r\nGAME.EXE\r\n".toByteArray(),
        "CD\\track01.iso" to ByteArray(150_000) { (it % 251).toByte() },
        "empty.txt" to ByteArray(0))

    @Test
    fun readsTheCurrentLayoutWithZlibAcrossItsParts() {
        val parts = write(Fixture.build(files, Layout.CURRENT, method = 1), partSize = 20_000)
        assertTrue("spanned", parts.size > 3)
        ClickteamInstaller.open(open(parts)).use { installer -> assertFiles(installer) }
    }

    @Test
    fun readsTheEarlierLayoutWithBzip2() {
        val parts = write(Fixture.build(files, Layout.EARLIER, method = 2), partSize = 50_000)
        ClickteamInstaller.open(open(parts)).use { installer -> assertFiles(installer) }
    }

    @Test
    fun recordsWithoutTimesOrOfTypeOneAreFilesAndCarryTheirShortcuts() {
        for (layout in Layout.entries) {
            val bytes = Fixture.build(files, layout, method = 1, untimed = setOf("dosbox.conf"),
                typeOne = setOf("CD\\track01.iso"), shortcuts = mapOf("HDD\\GAME\\GAME.EXE" to ("Game.lnk" to "-conf GAME.conf")))
            ClickteamInstaller.open(open(write(bytes, Int.MAX_VALUE))).use { installer ->
                assertFiles(installer)
                val shortcut = installer.members.single { it.shortcuts.isNotEmpty() }
                assertEquals("HDD/GAME/GAME.EXE", shortcut.name)
                assertEquals(listOf("Game.lnk" to "-conf GAME.conf"), shortcut.shortcuts.map { it.name to it.arguments })
            }
        }
    }

    @Test
    fun aRecordWhoseDataDoesNotTileTheDataBlockFailsTheInstaller() {
        val bytes = Fixture.build(files, Layout.CURRENT, method = 1, misplaced = "dosbox.conf")
        val failure = runCatching { ClickteamInstaller.open(open(write(bytes, Int.MAX_VALUE))).close() }.exceptionOrNull()
        assertTrue("$failure", failure is IOException)
    }

    @Test
    fun aProgramIsAnInstallerOnlyWithTheSignatureAfterItsSections() {
        val installer = write(Fixture.build(files, Layout.CURRENT, method = 1), partSize = Int.MAX_VALUE).single()
        FileChannel.open(installer.toPath()).use { assertTrue(ClickteamInstaller.isInstaller(it)) }
        val plain = folder.newFile("plain.exe").apply { writeBytes(Fixture.pe() + ByteArray(64)) }
        FileChannel.open(plain.toPath()).use { assertFalse(ClickteamInstaller.isInstaller(it)) }
        val text = folder.newFile("readme.exe").apply { writeText("MZ is not enough to be a program, nor is this text.") }
        FileChannel.open(text.toPath()).use { assertFalse(ClickteamInstaller.isInstaller(it)) }
    }

    @Test
    fun aMissingPartNamesItsIndex() {
        val parts = write(Fixture.build(files, Layout.CURRENT, method = 1), partSize = 20_000)
        try {
            ClickteamInstaller.open(open(parts.take(2))).close()
            fail("opened without its parts")
        } catch (missing: ClickteamInstaller.MissingPart) {
            assertEquals(2, missing.index)
        }
        assertEquals("Games/Install-ABC.D02", ClickteamInstaller.partName("Games/Install-ABC.exe", 2))
    }

    @Test
    fun aDamagedStreamFailsItsRead() {
        val bytes = Fixture.build(files, Layout.CURRENT, method = 1)
        // A byte inside the game program's packed stream.
        bytes[Fixture.dataStart(bytes) + 2_000] = (bytes[Fixture.dataStart(bytes) + 2_000] + 1).toByte()
        val parts = write(bytes, partSize = Int.MAX_VALUE)
        ClickteamInstaller.open(open(parts)).use { installer ->
            val member = installer.members.first { it.name == "HDD/GAME/GAME.EXE" }
            val failure = runCatching { installer.open(member).use { it.readBytes() } }.exceptionOrNull()
            assertTrue("$failure", failure is IOException)
        }
    }

    @Test
    fun aPathOutsideItsFolderFailsTheInstaller() {
        val parts = write(Fixture.build(mapOf("..\\AUTOEXEC.BAT" to byteArrayOf(1)), Layout.CURRENT, method = 1), Int.MAX_VALUE)
        val failure = runCatching { ClickteamInstaller.open(open(parts)).close() }.exceptionOrNull()
        assertTrue("$failure", failure is IOException)
    }

    @Test
    fun theDigestFollowsTheFileList() {
        fun digest(content: Map<String, ByteArray>) =
            ClickteamInstaller.open(open(write(Fixture.build(content, Layout.CURRENT, 1), Int.MAX_VALUE))).use { it.contentDigest() }
        assertArrayEquals(digest(files), digest(files))
        assertFalse(digest(files).contentEquals(digest(files + ("extra.txt" to byteArrayOf(7)))))
    }

    private fun assertFiles(installer: ClickteamInstaller) {
        assertEquals(files.keys.map { it.replace('\\', '/') }, installer.members.map { it.name })
        for (member in installer.members) {
            val expected = files.getValue(member.name.replace('/', '\\'))
            assertEquals(expected.size.toLong(), member.size)
            assertArrayEquals(member.name, expected, installer.open(member).use { it.readBytes() })
        }
    }

    /** [bytes] as an installer program and parts of [partSize] bytes each after the program's own (`.D01` on). */
    private fun write(bytes: ByteArray, partSize: Int): List<File> {
        val directory = folder.newFolder()
        val out = ArrayList<File>()
        var at = 0
        var index = 0
        while (at < bytes.size) {
            val count = minOf(partSize.toLong(), (bytes.size - at).toLong()).toInt()
            val name = if (index == 0) "Install-ABC.exe" else ClickteamInstaller.partName("Install-ABC.exe", index)
            val header = if (index == 0) ByteArray(0) else byteArrayOf(0x5A, 0x5A, 0x5A, index.toByte())
            out += File(directory, name).apply { writeBytes(header + bytes.copyOfRange(at, at + count)) }
            at += count
            index++
        }
        return out
    }

    private fun open(files: List<File>) = files.map { file ->
        val channel = FileChannel.open(file.toPath(), StandardOpenOption.READ)
        ClickteamInstaller.Part(channel, channel)
    }

    private enum class Layout { CURRENT, EARLIER }

    /** Installers made the way Install Creator lays them out (ClickteamInstaller's description). */
    private object Fixture {
        private const val OVERLAY = 0xA0

        /** A PE program of one section that ends at [OVERLAY]. */
        fun pe(): ByteArray {
            val out = ByteBuffer.allocate(OVERLAY).order(ByteOrder.LITTLE_ENDIAN)
            out.putShort(0, 0x5A4D).putInt(0x3C, 0x40)
            out.putInt(0x40, 0x00004550).putShort(0x46, 1).putShort(0x54, 0)
            // The section table follows the empty optional header: raw size 0x20 at 0x80.
            out.putInt(0x58 + 16, 0x20).putInt(0x58 + 20, 0x80)
            return out.array()
        }

        fun dataStart(installer: ByteArray): Int {
            val buffer = ByteBuffer.wrap(installer).order(ByteOrder.LITTLE_ENDIAN)
            var at = OVERLAY + 6
            while (buffer.getShort(at).toInt() and 0xFFFF != ClickteamInstaller.DATA) at += 8 + buffer.getInt(at + 4)
            return at + 8
        }

        /**
         * An installer of [content]. The files in [untimed] have no FILETIMEs, those in [typeOne]
         * records of type 1; [shortcuts] gives a file a Start menu shortcut (its name and arguments);
         * [misplaced] names a file whose record's offset is one byte off.
         */
        fun build(content: Map<String, ByteArray>, layout: Layout, method: Int, untimed: Set<String> = emptySet(),
                  typeOne: Set<String> = emptySet(), shortcuts: Map<String, Pair<String, String>> = emptyMap(),
                  misplaced: String? = null): ByteArray {
            // The data block: 4 bytes, then each file's method and whole stream, whose last 4 bytes
            // are the next file's first 4.
            val data = ByteArrayOutputStream().apply { write(byteArrayOf(1, 2, 3, 4)) }
            val records = ArrayList<ByteArray>()
            records += ByteArray(if (layout == Layout.CURRENT) 40 else 44).also { record ->
                header(record, layout, 2)
            }
            for ((name, bytes) in content) {
                val offset = data.size() - 4
                // An empty file is its method byte alone.
                val stream = if (bytes.isEmpty()) ByteArray(0) else pack(bytes, method)
                data.write(method)
                data.write(stream)
                records += record(name, bytes.size, offset + if (name == misplaced) 1 else 0, stream.size + 1, layout,
                    timed = name !in untimed, type = if (name in typeOne) 1 else 0, shortcut = shortcuts[name])
            }
            val list = ByteArrayOutputStream()
            list.write(le32(records.size))
            records.forEach(list::write)
            val overlay = ByteArrayOutputStream()
            overlay.write(ClickteamInstaller.SIGNATURE)
            overlay.write(block(0x1435, le32(4) + byteArrayOf(1) + pack(byteArrayOf(9, 9, 9, 9), 1)))
            overlay.write(block(ClickteamInstaller.FILE_LIST, le32(list.size()) + byteArrayOf(method.toByte()) +
                pack(list.toByteArray(), method)))
            overlay.write(block(ClickteamInstaller.DATA, data.toByteArray()))
            return pe() + overlay.toByteArray()
        }

        private fun header(record: ByteArray, layout: Layout, type: Int) {
            val buffer = ByteBuffer.wrap(record).order(ByteOrder.LITTLE_ENDIAN)
            if (layout == Layout.CURRENT) buffer.putShort(0, record.size.toShort()).putShort(2, type.toShort())
            else buffer.putInt(0, record.size).putInt(4, type)
        }

        private fun record(name: String, size: Int, offset: Int, packed: Int, layout: Layout, timed: Boolean, type: Int,
                           shortcut: Pair<String, String>?): ByteArray {
            // Without FILETIMEs the path starts 24 bytes sooner.
            val nameAt = (if (layout == Layout.CURRENT) 68 else 64) - if (timed) 0 else 24
            // After the path: the Start menu group, then the Start menu and desktop shortcuts' name, arguments and icon.
            val tail = if (shortcut == null) ByteArray(7) else
                ("Collection Chamber\u0000${shortcut.first}\u0000${shortcut.second}\u0000Icon.ico\u0000\u0000\u0000\u0000\u0000")
                    .toByteArray(Charsets.ISO_8859_1)
            val encoded = name.toByteArray(Charsets.ISO_8859_1)
            val record = ByteArray(nameAt + encoded.size + 1 + tail.size)
            header(record, layout, type)
            encoded.copyInto(record, nameAt)
            tail.copyInto(record, nameAt + encoded.size + 1)
            val buffer = ByteBuffer.wrap(record).order(ByteOrder.LITTLE_ENDIAN)
            val flags = (if (timed) 0x08 else 0x00) or 0x00CFE000
            if (layout == Layout.CURRENT) buffer.putInt(6, offset).putInt(10, packed).putInt(18, size).putInt(22, name.hashCode())
                .putInt(26, flags)
            else buffer.putInt(8, flags).putInt(20, name.hashCode()).putInt(24, size).putInt(28, offset).putInt(32, packed)
            return record
        }

        private fun block(id: Int, body: ByteArray): ByteArray =
            ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putShort(id.toShort()).putShort(1).putInt(body.size).array() + body

        private fun pack(bytes: ByteArray, method: Int): ByteArray = ByteArrayOutputStream().also { out ->
            (if (method == 1) DeflaterOutputStream(out) else BZip2CompressorOutputStream(out)).use { it.write(bytes) }
        }.toByteArray()

        private fun le32(value: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    }
}
