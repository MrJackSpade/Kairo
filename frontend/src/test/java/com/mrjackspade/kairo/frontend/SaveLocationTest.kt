package com.mrjackspade.kairo.frontend

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class SaveLocationTest {
    private val root: File = Files.createTempDirectory("save-location").toFile()
    private val saves = AppSaveLocation(File(root, "saves"))
    private val kinds = StateSlotStore.KINDS + ("sav" to "overlay")

    @After fun cleanUp() { root.deleteRecursively() }

    private fun content(text: String) = File(root, "content-${text.hashCode()}").apply {
        mkdirs()
        File(this, "state").writeText(text)
    }

    @Test fun aUnitIsTheGamesPathWithItsKind() {
        val unit = SaveUnit.of(GameKey("Adventure/Star Control 2.hdi"), "state1")
        saves.import(unit, content("one"))
        assertEquals("one", File(saves.root, "Adventure/Star Control 2.hdi.state1/state").readText())
        assertEquals(listOf(unit), saves.units(kinds))
    }

    @Test fun everyUnitIsListedWhereverItIs() {
        val units = listOf(
            SaveUnit.of(GameKey("Top.hdi"), "state2"),
            SaveUnit.of(GameKey("a/b/c/Deep.hdi"), "state4"),
            SaveUnit.of(GameKey("Set.zip/Disk 1.d88"), "state1"),
            SaveUnit.of(GameKey("../outside/x.hdi"), "state3"),
            SaveUnit.of(GameKey("Doom.zip"), "sav"))
        for (unit in units) saves.import(unit, content(unit.path))
        val v1 = SaveUnit.v1(GameKey("Old.hdi").hashName, "slot1")
        saves.import(v1, content("v1"))
        val contentId = SaveUnit.v1(SaveLocation.contentIdName("sha256-hdi-v1:ab12"), "slot2")
        saves.import(contentId, content("id"))
        assertEquals((units + v1 + contentId).toSet(), saves.units(kinds).toSet())
        assertEquals(setOf(v1.game, contentId.game), saves.v1Games())
    }

    @Test fun aFolderNamedLikeAnEarlierGameInsideTheLibraryIsNotOne() {
        val unit = SaveUnit.of(GameKey("Games/overlay/x.hdi"), "state1")
        saves.import(unit, content("x"))
        assertEquals(listOf(unit), saves.units(kinds))
        assertTrue(saves.v1Games().isEmpty())
    }

    @Test fun deletingTheLastUnitRemovesTheFoldersItLeavesEmpty() {
        val deep = SaveUnit.of(GameKey("a/b/Deep.hdi"), "state1")
        val beside = SaveUnit.of(GameKey("a/Other.hdi"), "state1")
        saves.import(deep, content("deep"))
        saves.import(beside, content("beside"))
        saves.delete(deep)
        assertFalse(File(saves.root, "a/b").exists())
        assertTrue(File(saves.root, "a/Other.hdi.state1").isDirectory)
        saves.delete(beside)
        assertFalse(File(saves.root, "a").exists())
        assertTrue(saves.root.isDirectory)
    }

    @Test fun anInterruptedImportIsRecoveredWhenListed() {
        val unit = SaveUnit.of(GameKey("Games/x.hdi"), "state1")
        val old = File(saves.root, "Games/x.hdi.state1.old").apply { mkdirs() }
        File(old, "state").writeText("kept")
        assertEquals(listOf(unit), saves.units(kinds))
        assertEquals("kept", File(saves.directory(unit), "state").readText())
        assertFalse(old.exists())
    }

    @Test fun anUnfinishedCopyIsNotAUnit() {
        File(saves.root, "Games/x.hdi.state1.moving").mkdirs()
        File(saves.root, "Games/x.hdi.state1.part").mkdirs()
        assertTrue(saves.units(kinds).isEmpty())
    }

    @Test fun exportCopiesAUnitOut() {
        val unit = SaveUnit.of(GameKey("x.hdi"), "sav")
        saves.import(unit, content("saved"))
        val out = File(root, "out")
        saves.export(unit, out)
        assertEquals("saved", File(out, "state").readText())
    }

    @Test fun noUnitStepsOutOfTheRoot() {
        for (path in listOf("../x", "a/../../x", "/x", "a//x", ".")) {
            assertThrows(path, IllegalArgumentException::class.java) { saves.directory(SaveUnit(path, "state1", v1 = true)) }
        }
        val outside = SaveUnit.of(GameKey("../../x.hdi"), "state1")
        assertTrue(saves.directory(outside).canonicalPath.startsWith(saves.root.canonicalPath + File.separator))
    }

    @Test fun aMoveKeepsEveryFileAndItsTime() {
        val from = SaveUnit.v1(GameKey("Old.hdi").hashName, "slot1")
        val to = SaveUnit.of(GameKey("Games/Old.hdi"), "state1")
        saves.import(from, content("old"))
        val state = File(saves.directory(from), "state").apply { setLastModified(1_234_567_000L) }
        saves.move(from, to)
        assertFalse(state.exists())
        assertEquals("old", File(saves.directory(to), "state").readText())
        assertEquals(1_234_567_000L, File(saves.directory(to), "state").lastModified())
        assertFalse(saves.movePending(from, to))
        assertThrows(IllegalStateException::class.java) { saves.move(from, to) }
        saves.import(from, content("again"))
        assertThrows(IllegalStateException::class.java) { saves.move(from, to) }
    }

    @Test fun anEarlierGamesLeftoversGoWithIt() {
        val name = GameKey("Old.hdi").hashName
        File(saves.root, "$name/slot1.part").mkdirs()
        saves.deleteV1Game(name)
        assertFalse(File(saves.root, name).exists())
        assertThrows(IllegalArgumentException::class.java) { saves.deleteV1Game("Games") }
    }
}
