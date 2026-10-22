package com.mrjackspade.kairo.frontend

import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.MessageDigest

class HexTest {
    @Test fun matchesFormatForEveryByte() {
        val bytes = ByteArray(256) { it.toByte() }
        assertEquals(bytes.joinToString("") { "%02x".format(it) }, bytes.toHex())
    }

    /** Saves, settings and working copies are named by storageName: it must not change. */
    @Test fun storageNameIsUnchanged() {
        for (path in listOf("MSAFILES/OOZE.MSA", "Games/Gauntlet II (1987)(U.S. Gold)(Disk 1 of 2).zip/GAUNT.ST", "", "ä/ü.st")) {
            val before = GameKey.STORAGE_PREFIX + MessageDigest.getInstance("SHA-256")
                .digest(path.toByteArray(Charsets.UTF_8)).take(16).joinToString("") { "%02x".format(it) }
            assertEquals(before, GameKey(path).storageName)
        }
    }
}
