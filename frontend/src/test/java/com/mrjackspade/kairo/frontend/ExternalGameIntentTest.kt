package com.mrjackspade.kairo.frontend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalGameIntentTest {
    private val library = "1234-ABCD:Emulation/roms/pc98"

    @Test fun shareStoragePathsHaveTheirDocumentIds() {
        assertEquals("primary:Emulation/roms/pc98/a.hdi", ExternalGameIntent.documentIdOf("/storage/emulated/0/Emulation/roms/pc98/a.hdi"))
        assertEquals("primary:a.hdi", ExternalGameIntent.documentIdOf("/storage/emulated/10/a.hdi"))
        assertEquals("primary:Games/a.hdi", ExternalGameIntent.documentIdOf("/sdcard/Games/a.hdi"))
        assertEquals("primary:Games/a.hdi", ExternalGameIntent.documentIdOf("/storage/self/primary/Games/a.hdi"))
        assertEquals("primary:Games/a.hdi", ExternalGameIntent.documentIdOf("/mnt/sdcard/Games/a.hdi"))
        assertEquals("1234-ABCD:Emulation/roms/pc98/Star Control 2.hdi",
            ExternalGameIntent.documentIdOf("/storage/1234-ABCD/Emulation/roms/pc98/Star Control 2.hdi"))
    }

    @Test fun otherPathsHaveNone() {
        assertNull(ExternalGameIntent.documentIdOf("/data/user/0/com.frontend/files/a.hdi"))
        assertNull(ExternalGameIntent.documentIdOf("relative/a.hdi"))
        assertNull(ExternalGameIntent.documentIdOf("/storage/emulated/0"))
        assertNull(ExternalGameIntent.documentIdOf("/storage/1234-ABCD/../emulated/0/a.hdi"))
        assertNull(ExternalGameIntent.documentIdOf("/storage/emulated/0/./a.hdi"))
    }

    @Test fun aFileInTheLibraryIsItsPathThere() {
        assertEquals("Star Control 2.hdi", ExternalGameIntent.relativePath("1234-ABCD:Emulation/roms/pc98/Star Control 2.hdi", library))
        assertEquals("Adventure/Set.zip", ExternalGameIntent.relativePath("1234-ABCD:Emulation/roms/pc98/Adventure/Set.zip", library))
    }

    @Test fun aFileBesideTheLibraryStepsOutOfIt() {
        assertEquals("../other/x.hdi", ExternalGameIntent.relativePath("1234-ABCD:Emulation/roms/other/x.hdi", library))
        assertEquals("../../../x.hdi", ExternalGameIntent.relativePath("1234-ABCD:x.hdi", library))
        assertEquals("../pc98 extra/x.hdi", ExternalGameIntent.relativePath("1234-ABCD:Emulation/roms/pc98 extra/x.hdi", library))
        assertEquals("a/x.hdi", ExternalGameIntent.relativePath("primary:a/x.hdi", "primary:"))
    }

    @Test fun aFileOnAnotherVolumeHasNoRelativePath() {
        assertNull(ExternalGameIntent.relativePath("primary:Emulation/roms/pc98/x.hdi", library))
        assertNull(ExternalGameIntent.relativePath("5678-EF01:Emulation/roms/pc98/x.hdi", library))
        assertNull(ExternalGameIntent.relativePath("no-volume", library))
    }

    @Test fun theFolderItselfIsNoGame() {
        assertNull(ExternalGameIntent.relativePath(library, library))
        assertNull(ExternalGameIntent.relativePath("1234-ABCD:Emulation/roms", library))
    }

    @Test fun twoDevicesWithDifferentCardsGiveOneKey() {
        val first = ExternalGameIntent.documentIdOf("/storage/1234-ABCD/Emulation/roms/pc98/Star Control 2.hdi")!!
        val second = ExternalGameIntent.documentIdOf("/storage/9F2C-0B11/Emulation/roms/pc98/Star Control 2.hdi")!!
        assertEquals(ExternalGameIntent.relativePath(first, library),
            ExternalGameIntent.relativePath(second, "9F2C-0B11:Emulation/roms/pc98"))
    }
}
