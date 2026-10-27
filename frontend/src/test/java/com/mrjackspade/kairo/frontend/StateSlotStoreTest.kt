package com.mrjackspade.kairo.frontend

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class StateSlotStoreTest {
    private val root: File = Files.createTempDirectory("state-slots").toFile()
    private val game = GameKey("Adventure/Star Control 2.hdi")
    private val store = StateSlotStore(root, game, "state.bin")

    @After fun cleanUp() { root.deleteRecursively() }

    private fun save(index: Int, text: String) {
        val scratch = store.beginSave(index)
        store.stateFileIn(scratch).writeText(text)
        store.commitSave(index, scratch) { it.writeText("png") }
    }

    @Test fun aSlotIsBesideTheGamesOtherSaves() {
        save(2, "two")
        assertEquals("two", File(root, "Adventure/Star Control 2.hdi.state2/state.bin").readText())
        assertEquals("png", File(root, "Adventure/Star Control 2.hdi.state2/thumbnail.png").readText())
        assertEquals(listOf(SaveUnit.of(game, "state2")), AppSaveLocation(root).units(StateSlotStore.KINDS))
    }

    @Test fun aSaveReplacesTheSlotAndLoadsBack() {
        save(1, "first")
        save(1, "second")
        assertEquals("second", store.withSlot(1) { File(it, "state.bin").readText() })
        assertFalse(File(root, "Adventure/Star Control 2.hdi.state1.old").exists())
        assertNull(store.slot(3).savedAt)
    }

    @Test fun anAbandonedFirstSaveLeavesNothing() {
        val scratch = store.beginSave(4)
        store.abandonSave(4, scratch)
        assertTrue(root.list()!!.isEmpty())
    }

    @Test fun aSaveCutShortKeepsThePreviousOne() {
        save(1, "kept")
        val slot = File(root, "Adventure/Star Control 2.hdi.state1")
        assertTrue(slot.renameTo(File(slot.path + ".old")))
        assertEquals("kept", store.withSlot(1) { File(it, "state.bin").readText() })
    }

    @Test fun kindsNameTheSlotsAsBeforeAndNow() {
        assertEquals(mapOf("state1" to "slot1", "state2" to "slot2", "state3" to "slot3", "state4" to "slot4"),
            StateSlotStore.KINDS)
    }
}
