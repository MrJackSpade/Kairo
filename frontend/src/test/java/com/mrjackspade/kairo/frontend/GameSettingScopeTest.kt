package com.mrjackspade.kairo.frontend

import org.junit.Assert.*
import org.junit.Test

class GameSettingScopeTest {
    private val preferences = memoryPreferences()
    private val scope = GameSettingScope(preferences)

    @Test fun aSettingIsNamedByTheGamesPath() {
        scope.setInt(GameKey("Games/Doom.zip"), "cycles_mode", 2)
        assertEquals(2, preferences.all["game_setting_v3_cycles_mode:Games/Doom.zip"])
        assertEquals(2, scope.int(GameKey("Games/Doom.zip"), "cycles_mode", 0))
        assertEquals(0, scope.int(GameKey("Games/Doom"), "cycles_mode", 0))
    }

    @Test fun gamesWhosePathsShareAStartKeepTheirOwnSettings() {
        val short = GameKey("a")
        val long = GameKey("a_b")
        val colon = GameKey("a:b")
        scope.setInt(short, "b_c", 1)
        scope.setInt(long, "c", 2)
        scope.setInt(colon, "c", 3)
        assertEquals(1, scope.int(short, "b_c", 0))
        assertEquals(2, scope.int(long, "c", 0))
        assertEquals(3, scope.int(colon, "c", 0))
        assertEquals(listOf("game_setting_v3_b_c:a"), scope.names(short))
        assertEquals(listOf("game_setting_v3_c:a:b"), scope.names(colon))
    }

    @Test fun aGamesSettingsMoveToAnotherGame() {
        val installer = GameKey("Installers/Doom.zip")
        val installed = GameKey("Games/Doom.zip")
        scope.setInt(installer, "cycles_mode", 2)
        scope.setBoolean(installer, "direct_touch", true)
        scope.setInt(GameKey("Installers/Doom.zip2"), "cycles_mode", 9)
        val editor = preferences.edit()
        scope.move(installer, installed, editor)
        assertTrue(editor.commit())
        assertEquals(2, scope.int(installed, "cycles_mode", 0))
        assertTrue(scope.boolean(installed, "direct_touch", false))
        assertTrue(scope.names(installer).isEmpty())
        assertEquals(9, scope.int(GameKey("Installers/Doom.zip2"), "cycles_mode", 0))
    }

    @Test fun aSettingNameHoldsNoColon() {
        assertThrows(IllegalArgumentException::class.java) { scope.setInt(GameKey("a"), "x:y", 1) }
    }
}
