package com.mrjackspade.kairo.frontend

import android.content.SharedPreferences
import androidx.annotation.StringRes

/** When the on-screen controls show. Auto hides them while a physical controller is connected. */
enum class OnScreenMode(@StringRes val label: Int, @StringRes val description: Int) {
    AUTO(R.string.kairo_auto, R.string.kairo_onscreen_mode_auto_help),
    SHOWN(R.string.kairo_onscreen_mode_shown, R.string.kairo_onscreen_mode_shown_help),
    HIDDEN(R.string.kairo_onscreen_mode_hidden, R.string.kairo_onscreen_mode_hidden_help);

    fun shown(controllerConnected: Boolean) = when (this) {
        AUTO -> !controllerConnected
        SHOWN -> true
        HIDDEN -> false
    }

    fun save(preferences: SharedPreferences) {
        preferences.edit().putString(KEY, name).apply()
    }

    companion object {
        private const val KEY = "onscreen_mode_v1"
        private const val LEGACY_KEY = "onscreen_enabled"

        /** An explicit legacy switch keeps its meaning; an untouched one becomes Auto. */
        fun load(preferences: SharedPreferences): OnScreenMode {
            preferences.getString(KEY, null)?.let { return valueOf(it) }
            if (!preferences.contains(LEGACY_KEY)) return AUTO
            return if (preferences.getBoolean(LEGACY_KEY, false)) SHOWN else HIDDEN
        }
    }
}
