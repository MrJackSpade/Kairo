package com.mrjackspade.kairo.frontend

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class GamePathMigrationTest {
    private data class Item(override val id: String) : LibraryItem {
        override val contentId: String? get() = "sha256-hdi-v1:00"
        override val catalogId: String? get() = contentId
        override val uri: String get() = "content://test/$id"
        override val path: String get() = id
        override val zipEntry: String? get() = null
        override val error: String? get() = null
        override val displayName: String get() = id.substringAfterLast('/')
        override val playable: Boolean get() = true
        override val mediaLabel: String get() = "Test"
    }

    private val root: File = Files.createTempDirectory("game-paths").toFile()
    private val saves = AppSaveLocation(File(root, "states"))
    private val preferences = memoryPreferences()
    private val star = Item("Adventure/Star Control 2.hdi")
    private val other = Item("Other.hdi")
    /** The key v1.4.0 gave the game when a frontend sent this URI. */
    private val frontendUri = "content://com.frontend.provider/roms/Star%20Control%202.hdi"

    @After fun cleanUp() { root.deleteRecursively() }

    private fun v1Save(name: String, kind: String, text: String, at: Long = 1_000_000L) {
        val directory = File(saves.root, "$name/$kind").apply { mkdirs() }
        File(directory, "state").apply { writeText(text); setLastModified(at) }
    }

    private fun saveAt(item: Item, kind: String) = File(saves.directory(SaveUnit.of(item.key, kind)), "state")

    private fun saveStore(location: SaveLocation = saves) =
        GamePathMigration.SaveUnitStore<Item>({ location }, StateSlotStore.KINDS, { "Slot ${it.removePrefix("state")}" })

    private fun migration(stores: List<GamePathMigration.Store<Item>> = listOf(saveStore()), earlierPending: Boolean = false,
                          resolve: (List<GamePathMigration.Conflict>) -> Set<GamePathMigration.Conflict> = { error("Unexpected conflict") }) =
        GamePathMigration(preferences, stores, { earlierPending }, resolve)

    @Test fun savesMoveToTheGamesPath() {
        v1Save(star.key.hashName, "slot1", "one")
        v1Save(star.key.hashName, "slot3", "three")
        File(saves.root, "${star.key.hashName}/slot2.part").mkdirs()
        val migration = migration()
        assertTrue(migration.claim(listOf(GamePathMigration.Claim(star)), every = true).isEmpty())
        assertEquals("one", saveAt(star, "state1").readText())
        assertEquals("three", saveAt(star, "state3").readText())
        assertEquals(1_000_000L, saveAt(star, "state1").lastModified())
        assertFalse(File(saves.root, star.key.hashName).exists())
        assertFalse(migration.pending)
        assertTrue(migration.claim(listOf(GamePathMigration.Claim(star)), every = true).isEmpty())
    }

    @Test fun savesOfGamesNotInTheLibraryWaitForThem() {
        v1Save(star.key.hashName, "slot1", "one")
        v1Save(other.key.hashName, "slot1", "other")
        val migration = migration()
        assertTrue(migration.claim(listOf(GamePathMigration.Claim(star)), every = true).isEmpty())
        assertTrue(migration.pending)
        assertTrue(File(saves.root, other.key.hashName).isDirectory)
        assertTrue(migration.claim(listOf(GamePathMigration.Claim(star), GamePathMigration.Claim(other)), every = true).isEmpty())
        assertEquals("other", saveAt(other, "state1").readText())
        assertFalse(migration.pending)
    }

    @Test fun aFrontendLaunchesSavesJoinTheLibraryGame() {
        v1Save(GameKey(frontendUri).hashName, "slot2", "from frontend")
        val claim = GamePathMigration.Claim(star, GameKey(frontendUri).hashName)
        assertTrue(claim.external)
        assertTrue(migration().claim(listOf(claim), every = false).isEmpty())
        assertEquals("from frontend", saveAt(star, "state2").readText())
    }

    @Test fun aFrontendsFileLooksOnlyForItsOwnEarlierNames() {
        v1Save(GameKey(frontendUri).hashName, "slot1", "from frontend")
        v1Save(other.key.hashName, "slot1", "other")
        var listed = 0
        val counting = object : SaveLocation by saves {
            override fun v1Games(): Set<String> { listed++; return saves.v1Games() }
        }
        val migration = migration(listOf(saveStore(counting)))
        assertTrue(migration.claim(listOf(GamePathMigration.Claim(star, GameKey(frontendUri).hashName)), every = false).isEmpty())
        assertEquals("from frontend", saveAt(star, "state1").readText())
        assertEquals(0, listed)
        // Another game's earlier saves wait for the library, which is not done with them.
        assertTrue(File(saves.root, other.key.hashName).isDirectory)
        assertTrue(migration.pending)
        // A file with nothing earlier finds nothing and changes nothing.
        assertTrue(migration.claim(listOf(GamePathMigration.Claim(star, GameKey("content://x/y.hdi").hashName)), every = false).isEmpty())
        assertTrue(migration.pending)
    }

    @Test fun aSaveTheGameAlreadyHasIsAConflictThePlayerSettles() {
        v1Save(star.key.hashName, "slot1", "library", at = 2_000_000L)
        v1Save(GameKey(frontendUri).hashName, "slot1", "frontend", at = 1_000_000L)
        v1Save(GameKey(frontendUri).hashName, "slot2", "only frontend")
        assertTrue(migration().claim(listOf(GamePathMigration.Claim(star)), every = true).isEmpty())
        var asked: List<GamePathMigration.Conflict> = emptyList()
        val errors = migration { conflicts -> asked = conflicts; emptySet() }
            .claim(listOf(GamePathMigration.Claim(star, GameKey(frontendUri).hashName)), every = false)
        assertTrue(errors.isEmpty())
        val conflict = asked.single()
        assertEquals("state1", conflict.kind)
        assertEquals("Slot 1", conflict.label)
        assertTrue(conflict.external)
        assertEquals(1_000_000L, conflict.earlierSavedAt)
        assertEquals(2_000_000L, conflict.currentSavedAt)
        // The current copy was kept; the frontend's other slot had no conflict and moved.
        assertEquals("library", saveAt(star, "state1").readText())
        assertEquals("only frontend", saveAt(star, "state2").readText())
        assertFalse(File(saves.root, GameKey(frontendUri).hashName).exists())
    }

    @Test fun theEarlierCopyReplacesTheCurrentOneWhenChosen() {
        saves.import(SaveUnit.of(star.key, "state1"), File(root, "current").apply { mkdirs(); File(this, "state").writeText("current") })
        v1Save(star.key.hashName, "slot1", "earlier")
        assertTrue(migration { it.toSet() }.claim(listOf(GamePathMigration.Claim(star)), every = true).isEmpty())
        assertEquals("earlier", saveAt(star, "state1").readText())
    }

    @Test fun anUnchosenConflictMovesNothingThatConflictsAndSaysWhy() {
        saves.import(SaveUnit.of(star.key, "state1"), File(root, "current").apply { mkdirs(); File(this, "state").writeText("current") })
        v1Save(star.key.hashName, "slot1", "earlier")
        v1Save(other.key.hashName, "slot1", "other")
        val migration = migration { error("Saves were not moved; choose which to keep the next time the library opens") }
        val errors = migration.claim(listOf(GamePathMigration.Claim(star), GamePathMigration.Claim(other)), every = true)
        assertEquals(setOf(star.key), errors.keys)
        assertEquals(UiText.res(R.string.kairo_migration_failed,
            "Saves were not moved; choose which to keep the next time the library opens"), errors.getValue(star.key))
        assertEquals("current", saveAt(star, "state1").readText())
        assertTrue(File(saves.root, "${star.key.hashName}/slot1").isDirectory)
        assertEquals("other", saveAt(other, "state1").readText())
        assertTrue(migration.pending)
    }

    @Test fun aFailingStoreIsReportedForItsGameAndTheRestStillMove() {
        v1Save(star.key.hashName, "slot1", "one")
        val failing = object : GamePathMigration.Store<Item> {
            override fun refresh(claims: List<GamePathMigration.Claim<Item>>, every: Boolean) {}
            override fun has(hashName: String) = hashName == star.key.hashName
            override fun move(claim: GamePathMigration.Claim<Item>, keep: Set<GamePathMigration.Conflict>) = error("storage full")
            override fun isEmpty() = false
        }
        val migration = migration(listOf(failing, saveStore()))
        val errors = migration.claim(listOf(GamePathMigration.Claim(star)), every = true)
        assertEquals(UiText.res(R.string.kairo_migration_failed, "storage full"), errors[star.key])
        assertEquals("one", saveAt(star, "state1").readText())
        assertTrue(migration.pending)
    }

    @Test fun settingsMoveToTheGamesKeyAndKeepWhatItHas() {
        val hash = star.key.hashName
        preferences.edit()
            .putInt("game_setting_v2_${hash}_cycles_mode", 2)
            .putString("game_setting_v2_${hash}_landscape_anchor", "top")
            .putString("game_controller_layer_v3_$hash", "[1]")
            .putString("game_controller_v2_$hash", "[2]")
            .putString("game_setting_v3_landscape_anchor:${star.key.path}", "bottom")
            .putString("game_setting_v2_not-a-hash_cycles_mode", "kept")
            .putInt("game_setting_v2_${other.key.hashName}_cycles_mode", 5)
            .commit()
        val families = listOf(
            GamePathMigration.PreferenceFamily(GameSettingScope.V1_PREFIX, true) { game, name -> GameSettingScope.key(game, name!!) }) +
            listOf("game_controller_v2_", "game_controller_layer_v3_").map { prefix ->
                GamePathMigration.PreferenceFamily(prefix, false) { game, _ -> prefix + game.path }
            }
        val migration = migration(listOf(GamePathMigration.PreferenceStore(preferences, families)))
        assertTrue(migration.claim(listOf(GamePathMigration.Claim(star)), every = true).isEmpty())
        val scope = GameSettingScope(preferences)
        assertEquals(2, scope.int(star.key, "cycles_mode", 0))
        assertEquals("bottom", scope.string(star.key, "landscape_anchor"))
        assertEquals("[1]", preferences.all["game_controller_layer_v3_${star.key.path}"])
        assertEquals("[2]", preferences.all["game_controller_v2_${star.key.path}"])
        assertTrue(preferences.all.keys.none { it.contains(hash) })
        assertEquals("kept", preferences.all["game_setting_v2_not-a-hash_cycles_mode"])
        assertEquals(5, preferences.all["game_setting_v2_${other.key.hashName}_cycles_mode"])
        assertTrue(migration.pending)
        // A frontend's file reads only its own earlier name's settings.
        assertTrue(migration.claim(listOf(GamePathMigration.Claim(other)), every = false).isEmpty())
        assertEquals(5, scope.int(other.key, "cycles_mode", 0))
        assertEquals("kept", preferences.all["game_setting_v2_not-a-hash_cycles_mode"])
    }

    @Test fun itIsNotDoneWhileTheKeyMigrationCanStillAddData() {
        v1Save(star.key.hashName, "slot1", "one")
        val migration = migration(earlierPending = true)
        assertTrue(migration.claim(listOf(GamePathMigration.Claim(star)), every = true).isEmpty())
        assertEquals("one", saveAt(star, "state1").readText())
        assertTrue(migration.pending)
        assertFalse(migration().also { it.claim(emptyList(), every = true) }.pending)
    }

    @Test fun aSecondDeviceThatMovedFirstLeavesNothingToAsk() {
        // The save folder is shared: the other device already moved the slot and removed its earlier copy.
        saves.import(SaveUnit.of(star.key, "state1"), File(root, "moved").apply { mkdirs(); File(this, "state").writeText("moved") })
        val migration = migration()
        assertTrue(migration.claim(listOf(GamePathMigration.Claim(star)), every = true).isEmpty())
        assertEquals("moved", saveAt(star, "state1").readText())
        assertFalse(migration.pending)
    }
}
