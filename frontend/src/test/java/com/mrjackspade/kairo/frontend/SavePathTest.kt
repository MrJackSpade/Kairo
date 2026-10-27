package com.mrjackspade.kairo.frontend

import org.junit.Assert.*
import org.junit.Test

class SavePathTest {
    @Test fun aLibraryPathIsItsOwnSavePath() {
        assertEquals("Adventure/Star Control 2.hdi", GameKey("Adventure/Star Control 2.hdi").savePath)
        assertEquals("Games/Set.zip/Disk 1.d88", GameKey.inArchive("Games/Set.zip", "Disk 1.d88").savePath)
        assertEquals("ゲーム/同級生.hdi", GameKey("ゲーム/同級生.hdi").savePath)
    }

    @Test fun stepsOutOfTheFolderStayUnderTheSaveRoot() {
        assertEquals("%2E%2E/other/x.hdi", GameKey("../other/x.hdi").savePath)
        assertEquals("%2E%2E/%2E%2E/x.hdi", GameKey("../../x.hdi").savePath)
        assertEquals("a/%2E/b.hdi", GameKey("a/./b.hdi").savePath)
        for (path in listOf("../x", "../../x", "a/../../x", "./x")) {
            assertTrue(path, GameKey(path).savePath.split('/').none { it == "." || it == ".." })
        }
    }

    @Test fun charactersNoSaveFolderTakesAreEncoded() {
        assertEquals("content%3A/%/com.example/x.hdi", GameKey("content://com.example/x.hdi").savePath)
        assertEquals("a%3Ab%2Ac%3Fd%22e%3Cf%3Eg%7Ch%5C", SavePath.of("a:b*c?d\"e<f>g|h\\"))
        assertEquals("tab%09name", SavePath.of("tab\tname"))
        assertEquals("trailing%2E/space%20/x", SavePath.of("trailing./space /x"))
        assertEquals("100%25.hdi", SavePath.of("100%.hdi"))
    }

    @Test fun noTwoPathsShareASavePath() {
        val paths = listOf("../x", "%2E%2E/x", "%252E%252E/x", "a./x", "a%2E/x", "a/x", "a//x", "a/%/x",
            "a:x", "a%3Ax", "a /x", "a%20/x", "x.", "x%2E", ".x", "..x", "x..")
        assertEquals(paths.size, paths.map(SavePath::of).toSet().size)
    }

    @Test fun theHashNameIsTheNameVersion140StoredDataUnder() {
        // sha256("Games/Doom.zip"), first 16 bytes: what v1.4.0's GameKey.storageName was.
        assertEquals("k1-59a5ee308728e08d8aa53e84ddbb7a5e", GameKey("Games/Doom.zip").hashName)
        assertNotEquals(GameKey("Doom.zip").hashName, GameKey("DOOM.zip").hashName)
        assertTrue(GameKey.HASH_NAME.matches(GameKey("Games/a b:c.zip").hashName))
    }
}
