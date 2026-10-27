package com.mrjackspade.kairo.frontend

import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.MessageDigest

class HexTest {
    @Test fun matchesFormatForEveryByte() {
        val bytes = ByteArray(256) { it.toByte() }
        assertEquals(bytes.joinToString("") { "%02x".format(it) }, bytes.toHex())
    }

    /** v1.4.0 named saves, settings and working copies by hashName, and DOS working files still are: it must not change. */
    @Test fun hashNameIsUnchanged() {
        for (path in listOf("MSAFILES/OOZE.MSA", "Games/Gauntlet II (1987)(U.S. Gold)(Disk 1 of 2).zip/GAUNT.ST", "", "ä/ü.st")) {
            val before = GameKey.HASH_PREFIX + MessageDigest.getInstance("SHA-256")
                .digest(path.toByteArray(Charsets.UTF_8)).take(16).joinToString("") { "%02x".format(it) }
            assertEquals(before, GameKey(path).hashName)
        }
    }
}
