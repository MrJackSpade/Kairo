package com.mrjackspade.kairo.frontend

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class GameKeyMigrationTest {
    private data class Item(override val id: String, override val contentId: String?) : LibraryItem {
        override val catalogId: String? get() = contentId
        override val uri: String get() = "content://test/$id"
        override val path: String get() = id
        override val zipEntry: String? get() = null
        override val error: String? get() = null
        override val displayName: String get() = id
        override val playable: Boolean get() = contentId != null
        override val mediaLabel: String get() = "Test"
    }

    private val root: File = Files.createTempDirectory("game-keys").toFile()
    private val saves = AppSaveLocation(File(root, "saves"))
    private val journal = File(root, "journal.txt")
    private val first = Item("Games/Doom.zip", "sha256-dos-manifest-v1:aa")
    private val copy = Item("Copies/Doom.zip", "sha256-dos-manifest-v1:aa")
    private val other = Item("Games/Keen.zip", "sha256-dos-manifest-v1:bb")

    @After fun cleanUp() { root.deleteRecursively() }

    private fun claim(vararg items: Item) = items.map { GameKeyMigration.Claim(it, null) }

    private fun legacySave(contentId: String, unit: String, text: String) {
        val directory = File(saves.root, "${SaveLocation.contentIdName(contentId)}/$unit").apply { mkdirs() }
        File(directory, "SAVE.DAT").writeText(text)
    }

    private fun keyedSave(item: Item, unit: String) = File(saves.root, "${item.key.hashName}/$unit/SAVE.DAT")

    private fun saveStore(resolve: (List<GameKeyMigration.SaveStore.Conflict>) -> Set<GameKeyMigration.SaveStore.Conflict> =
                              { error("Unexpected conflict") }) =
        GameKeyMigration.SaveStore({ saves }, setOf("overlay"), root, journal, resolve)

    @Test fun keysKeepCaseAndStoreEveryPathAlike() {
        assertNotEquals(GameKey("Doom.zip").hashName, GameKey("DOOM.zip").hashName)
        assertEquals(GameKey("Doom.zip").hashName, GameKey("Doom.zip").hashName)
        assertTrue(GameKey("Games/a b:c.zip").hashName.matches(Regex("k1-[0-9a-f]{32}")))
        assertEquals("Games/set.zip/disk1.hdi", GameKey.inArchive("Games/set.zip", "disk1.hdi").path)
    }

    @Test fun settingsMoveToEveryGameWithThatContent() {
        val preferences = memoryPreferences(mapOf(
            "game_setting_v1_sha256-dos-manifest-v1:aa_cycles_mode" to 1,
            "controller_game_sha256-dos-manifest-v1:aa" to "[]",
            "game_setting_v1_sha256-dos-manifest-v1:cc_touch_mode" to 2))
        val migration = GameKeyMigration<Item>(preferences, listOf(GameKeyMigration.PreferenceStore(preferences, listOf(
            GameKeyMigration.PreferenceFamily(GameSettingScope.CONTENT_ID_PREFIX, GameSettingScope.V1_PREFIX, true),
            GameKeyMigration.PreferenceFamily("controller_game_", "game_controller_v2_", false)))))
        assertTrue(migration.claim(claim(first, copy, other)).isEmpty())
        for (item in listOf(first, copy)) {
            assertEquals(1, preferences.all["game_setting_v2_${item.key.hashName}_cycles_mode"])
            assertEquals("[]", preferences.all["game_controller_v2_${item.key.hashName}"])
        }
        assertNull(preferences.all["game_setting_v1_sha256-dos-manifest-v1:aa_cycles_mode"])
        assertFalse(preferences.all.keys.any { it.contains(other.key.hashName) })
        // A game not in the library keeps its settings until a scan finds it.
        assertEquals(2, preferences.all["game_setting_v1_sha256-dos-manifest-v1:cc_touch_mode"])
        assertTrue(migration.pending)
    }

    @Test fun savesCopyToEachGameThenTheEarlierCopyGoes() {
        legacySave(first.contentId!!, "overlay", "progress")
        val migration = GameKeyMigration<Item>(memoryPreferences(), listOf(saveStore()))
        assertTrue(migration.claim(claim(first, copy, other)).isEmpty())
        assertEquals("progress", keyedSave(first, "overlay").readText())
        assertEquals("progress", keyedSave(copy, "overlay").readText())
        assertFalse(File(saves.root, SaveLocation.contentIdName(first.contentId!!)).exists())
        assertFalse(migration.pending)
    }

    @Test fun unclaimedSavesWaitForTheirGame() {
        legacySave(other.contentId!!, "overlay", "keen")
        val migration = GameKeyMigration<Item>(memoryPreferences(), listOf(saveStore()))
        assertTrue(migration.claim(claim(first)).isEmpty())
        assertTrue(migration.pending)
        assertTrue(File(saves.root, SaveLocation.contentIdName(other.contentId!!)).isDirectory)
        assertTrue(migration.claim(claim(first, other)).isEmpty())
        assertEquals("keen", keyedSave(other, "overlay").readText())
        assertFalse(migration.pending)
    }

    @Test fun aSaveAlreadyUnderTheKeyIsAConflictTheUserSettles() {
        legacySave(first.contentId!!, "overlay", "earlier")
        keyedSave(first, "overlay").apply { parentFile!!.mkdirs(); writeText("newer") }
        var asked = emptyList<GameKeyMigration.SaveStore.Conflict>()
        GameKeyMigration<Item>(memoryPreferences(), listOf(saveStore { asked = it; emptySet() })).claim(claim(first))
        assertEquals(listOf(first.key), asked.map { it.game })
        assertEquals("newer", keyedSave(first, "overlay").readText())

        legacySave(first.contentId!!, "overlay", "earlier")
        GameKeyMigration<Item>(memoryPreferences(), listOf(saveStore { it.toSet() })).claim(claim(first))
        assertEquals("earlier", keyedSave(first, "overlay").readText())
    }

    @Test fun anInterruptedMoveResumesWithoutAskingAboutItsOwnCopies() {
        legacySave(first.contentId!!, "overlay", "progress")
        // The previous run copied to one game and stopped before the earlier copy was removed.
        keyedSave(first, "overlay").apply { parentFile!!.mkdirs(); writeText("progress") }
        journal.writeText("${SaveLocation.contentIdName(first.contentId!!)}/overlay")
        assertTrue(GameKeyMigration<Item>(memoryPreferences(), listOf(saveStore())).claim(claim(first, copy)).isEmpty())
        assertEquals("progress", keyedSave(copy, "overlay").readText())
        assertFalse(File(saves.root, SaveLocation.contentIdName(first.contentId!!)).exists())
        assertEquals("", journal.readText())
    }

    @Test fun aFailureIsReportedForItsGamesAndLeavesOthersMoving() {
        legacySave(other.contentId!!, "overlay", "keen")
        val failing = object : GameKeyMigration.ContentStore {
            override fun has(contentId: String) = contentId == first.contentId
            override fun move(contentId: String, games: List<GameKey>) = error("storage full")
            override fun isEmpty() = false
        }
        val migration = GameKeyMigration<Item>(memoryPreferences(), listOf(failing, saveStore()))
        val errors = migration.claim(claim(first, copy, other))
        assertEquals(setOf(first.key, copy.key), errors.keys)
        assertTrue(errors.getValue(first.key).contains("storage full"))
        assertEquals("keen", keyedSave(other, "overlay").readText())
        assertTrue(migration.pending)
    }

    @Test fun entryDataMovesToItsOneGame() {
        val moved = ArrayList<Pair<String, GameKey>>()
        val store = object : GameKeyMigration.EntryStore<Item> {
            override fun has(claim: GameKeyMigration.Claim<Item>) = claim.entryId == "old-1"
            override fun move(claim: GameKeyMigration.Claim<Item>) { moved += claim.entryId!! to claim.key }
            override fun isEmpty() = true
        }
        val migration = GameKeyMigration(memoryPreferences(), emptyList(), listOf(store))
        migration.claim(listOf(GameKeyMigration.Claim(first, "old-1"), GameKeyMigration.Claim(copy, "old-2")))
        assertEquals(listOf("old-1" to first.key), moved)
        assertFalse(migration.pending)
    }
}
