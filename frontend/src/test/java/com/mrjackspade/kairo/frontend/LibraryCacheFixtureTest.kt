package com.mrjackspade.kairo.frontend

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The library cache a device writes (library-v2.bin) against the fixture that
 * shared/tools/test_library_cache.py reads with library_cache.py (Kairo#106): a change to the
 * layout fails here until the fixture is written again, and then the Python test until its reader
 * follows. To write it again, run this test with the environment variable KAIRO_WRITE_FIXTURES=1.
 */
class LibraryCacheFixtureTest {
    private val fixture = File("../tools/fixtures/library-v2.bin")

    /** Every field of the current layout, each both set and empty where it can be. */
    private val entries = listOf(
        // A game's first disk with the rest of its set beside it, in an archive.
        DiskImageEntry("Games/Set.zip/Disk 1.d88", "content://tree/Set.zip", "Games/Set.zip", "Disk 1.d88",
            1_234_567L, 1_700_000_000_000L, 348_848L, 0x89ABCDEFL,
            "sha256-fd-v1:" + "a".repeat(64), null,
            listOf("Disk 2.d88", "Disk 3.d88"), "sha256-fd-v1:" + "b".repeat(64)),
        // A loose disk that failed to read, known by no catalog.
        DiskImageEntry("Games/Broken.hdm", "content://tree/Broken.hdm", "Games/Broken.hdm", null,
            1_261_568L, 1_600_000_000_000L, 0L, 0L, null, "Unreadable disk image", catalogId = null),
        // An archive with no disk image in it, kept out of the library.
        DiskImageEntry("Games/Manuals.zip", "content://tree/Manuals.zip", "Games/Manuals.zip", null,
            2_000L, 1_650_000_000_000L, 0L, 0L, null, holdsGame = false, catalogId = null),
        // A program and its folder, with a name outside ASCII.
        DiskImageEntry("Games/ゲーム/RUN.EXE", "content://tree/RUN.EXE", "Games/ゲーム/RUN.EXE", null,
            40_000L, 1_690_000_000_000L, 40_000L, 0xFFFFFFFFL, "sha256-fd-v1:" + "c".repeat(64),
            folder = listOf(FolderFile("DATA/LEVEL1.DAT", "content://tree/LEVEL1.DAT"), FolderFile("CONFIG.SYS", "content://tree/CONFIG.SYS"))))

    private val format = LibraryCacheFormat(DiskImageCacheRecords.LAYOUT, DiskImageCacheRecords::write, DiskImageCacheRecords::read)

    private fun written() = format.encode("content://tree", entries, JSONObject().put("mediaFormatsVersion", 1))

    @Test
    fun theAppWritesTheFixture() {
        if (System.getenv("KAIRO_WRITE_FIXTURES") == "1") {
            fixture.parentFile.mkdirs()
            fixture.writeBytes(written())
        }
        assertArrayEquals("library-v2.bin as the app writes it, against ${fixture.canonicalPath}",
            fixture.readBytes(), written())
    }

    @Test
    fun theAppReadsTheFixtureBack() {
        val snapshot = format.decode(fixture.readBytes())!!
        assertEquals("content://tree", snapshot.treeUri)
        assertEquals(entries, snapshot.entries)
        assertEquals(1, snapshot.root.getInt("mediaFormatsVersion"))
    }
}
