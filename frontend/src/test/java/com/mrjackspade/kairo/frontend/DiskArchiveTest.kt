package com.mrjackspade.kairo.frontend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiskArchiveTest {
    @Test
    fun archivesAreZipAnd7zByExtension() {
        assertTrue(DiskArchive.isArchive("Games/King's Quest.ZIP"))
        assertTrue(DiskArchive.isArchive("disk.7z"))
        assertFalse(DiskArchive.isArchive("disk.img"))
        assertFalse(DiskArchive.isArchive("zip"))
    }

    @Test
    fun safeNamesUseForwardSlashes() {
        assertEquals("KQ1/floppy/disk.img", DiskArchive.safeName("KQ1\\floppy\\disk.img"))
        assertEquals("KQ1/floppy", DiskArchive.safeName("KQ1/floppy/"))
    }

    @Test
    fun namesThatLeaveTheFolderFail() {
        for (name in listOf("../disk.img", "a/../../disk.img", "/disk.img", "C:/disk.img", "a//disk.img", "./disk.img", "", "a\u0000b")) {
            val failure = runCatching { DiskArchive.safeName(name) }.exceptionOrNull()
            assertTrue("$name should fail", failure is IllegalArgumentException)
        }
    }
}
