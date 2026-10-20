package com.mrjackspade.kairo.frontend

import android.content.SharedPreferences

/** Resolves game-scoped settings over global values without changing global defaults. */
class GameSettingScope(private val preferences: SharedPreferences) {
    private fun key(game: GameKey, name: String) = "${PREFIX}${game.storageName}_$name"

    fun has(game: GameKey?, name: String): Boolean =
        game != null && preferences.contains(key(game, name))

    fun int(game: GameKey?, name: String, global: Int): Int =
        if (game == null) global else preferences.getInt(key(game, name), global)

    fun boolean(game: GameKey?, name: String, global: Boolean): Boolean =
        if (game == null) global else preferences.getBoolean(key(game, name), global)

    fun setInt(game: GameKey, name: String, value: Int) {
        preferences.edit().putInt(key(game, name), value).apply()
    }

    fun setBoolean(game: GameKey, name: String, value: Boolean) {
        preferences.edit().putBoolean(key(game, name), value).apply()
    }

    fun clear(game: GameKey, vararg names: String) {
        val editor = preferences.edit()
        names.forEach { editor.remove(key(game, it)) }
        editor.apply()
    }

    companion object {
        const val PREFIX = "game_setting_v2_"
        /** Settings from before games had keys, named by content ID. */
        const val CONTENT_ID_PREFIX = "game_setting_v1_"
    }
}
