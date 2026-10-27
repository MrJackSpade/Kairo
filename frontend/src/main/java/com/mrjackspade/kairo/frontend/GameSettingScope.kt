package com.mrjackspade.kairo.frontend

import android.content.SharedPreferences

/**
 * Resolves game-scoped settings over global values without changing global defaults. A setting
 * is stored as `game_setting_v3_<name>:<game key>`: setting names hold no colon, so the key after
 * the first one is the game's whole [GameKey.path].
 */
class GameSettingScope(private val preferences: SharedPreferences) {
    fun has(game: GameKey?, name: String): Boolean =
        game != null && preferences.contains(key(game, name))

    fun int(game: GameKey?, name: String, global: Int): Int =
        if (game == null) global else preferences.getInt(key(game, name), global)

    fun boolean(game: GameKey?, name: String, global: Boolean): Boolean =
        if (game == null) global else preferences.getBoolean(key(game, name), global)

    /** The game's own text value, or null when it has none. */
    fun string(game: GameKey, name: String): String? = preferences.getString(key(game, name), null)

    fun setString(game: GameKey, name: String, value: String) {
        preferences.edit().putString(key(game, name), value).apply()
    }

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

    /** Each setting [game] has, by preference name. */
    fun names(game: GameKey): List<String> = preferences.all.keys.filter { stored ->
        stored.startsWith(PREFIX) && ':' in stored && stored.substringAfter(':') == game.path
    }

    /** Moves [from]'s settings to [to], replacing any [to] has, in [editor]. */
    fun move(from: GameKey, to: GameKey, editor: SharedPreferences.Editor) {
        val values = preferences.all
        for (stored in names(from)) {
            Preferences.put(editor, key(to, stored.removePrefix(PREFIX).substringBefore(':')), values[stored])
            editor.remove(stored)
        }
    }

    companion object {
        const val PREFIX = "game_setting_v3_"
        /** v1.4.0 and earlier: `game_setting_v2_<v1 name>_<name>` (GameKey.hashName). */
        const val V1_PREFIX = "game_setting_v2_"
        /** Settings from before games had keys, named by content ID. */
        const val CONTENT_ID_PREFIX = "game_setting_v1_"

        fun key(game: GameKey, name: String): String {
            require(':' !in name) { "A game setting's name holds no colon: $name" }
            return "$PREFIX$name:${game.path}"
        }
    }
}

/** Writing a value read from [SharedPreferences.getAll] back under another name. */
object Preferences {
    fun put(editor: SharedPreferences.Editor, name: String, value: Any?) {
        @Suppress("UNCHECKED_CAST")
        when (value) {
            is Int -> editor.putInt(name, value)
            is Long -> editor.putLong(name, value)
            is Float -> editor.putFloat(name, value)
            is Boolean -> editor.putBoolean(name, value)
            is String -> editor.putString(name, value)
            is Set<*> -> editor.putStringSet(name, value as Set<String>)
            else -> error("Unsupported setting $name")
        }
    }
}
