package com.mrjackspade.kairo.frontend

import android.content.SharedPreferences

/** Resolves game-scoped settings over global values without changing global defaults. */
class GameSettingScope(private val preferences: SharedPreferences) {
    private fun key(contentId: String, name: String) = "game_setting_v1_${contentId}_$name"

    fun has(contentId: String?, name: String): Boolean =
        contentId != null && preferences.contains(key(contentId, name))

    fun int(contentId: String?, name: String, global: Int): Int =
        if (contentId == null) global else preferences.getInt(key(contentId, name), global)

    fun boolean(contentId: String?, name: String, global: Boolean): Boolean =
        if (contentId == null) global else preferences.getBoolean(key(contentId, name), global)

    fun setInt(contentId: String, name: String, value: Int) {
        preferences.edit().putInt(key(contentId, name), value).apply()
    }

    fun setBoolean(contentId: String, name: String, value: Boolean) {
        preferences.edit().putBoolean(key(contentId, name), value).apply()
    }

    fun clear(contentId: String, vararg names: String) {
        val editor = preferences.edit()
        names.forEach { editor.remove(key(contentId, it)) }
        editor.apply()
    }
}
