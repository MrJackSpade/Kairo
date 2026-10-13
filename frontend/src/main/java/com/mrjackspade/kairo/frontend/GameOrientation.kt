package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.res.Configuration

/**
 * The screen orientation while a game runs, for all games. Auto leaves rotation to the device;
 * Landscape and Portrait lock it, still turning either way up unless the device's rotation is
 * locked. The library always follows the device.
 */
class GameOrientation(private val activity: Activity, private val preferences: SharedPreferences) {
    private var inGame = false

    private val selected: String get() = preferences.getString(KEY, AUTO)!!

    /**
     * A game runs with Landscape or Portrait selected but the window kept the other orientation,
     * as when a device setting such as a forced landscape mode overrides app requests. Read when
     * shown; the hosts refresh their pages on configuration changes, so a rotation that lands
     * late clears it.
     */
    private val blocked: Boolean get() {
        if (!inGame) return false
        val wanted = when (selected) {
            LANDSCAPE -> Configuration.ORIENTATION_LANDSCAPE
            PORTRAIT -> Configuration.ORIENTATION_PORTRAIT
            else -> return false
        }
        return activity.resources.configuration.orientation != wanted
    }

    fun row() = SettingsRow.choice(KEY, "Game orientation", ChoiceSpec("Game orientation",
        { GameSource.ALL_GAMES }, {
            listOf(ChoiceGroup(null, listOf(ChoiceOption(AUTO, "Auto"),
                ChoiceOption(LANDSCAPE, "Landscape"), ChoiceOption(PORTRAIT, "Portrait")),
                foot = if (blocked) BLOCKED else null))
        }, { selected }, select = { value ->
            preferences.edit().putString(KEY, value).apply()
            apply()
        }), tag = { if (blocked) "Not applied" else null })

    fun enterGame() {
        inGame = true
        apply()
    }

    fun leaveGame() {
        inGame = false
        apply()
    }

    private fun apply() {
        activity.requestedOrientation = if (!inGame) ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        else when (selected) {
            AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
            PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            else -> error("Unknown game orientation: $selected")
        }
    }

    companion object {
        private const val KEY = "game_orientation"
        private const val AUTO = "auto"
        private const val LANDSCAPE = "landscape"
        private const val PORTRAIT = "portrait"
        private const val BLOCKED = "Rotation was attempted but did not apply. A system setting is likely blocking it."
    }
}
