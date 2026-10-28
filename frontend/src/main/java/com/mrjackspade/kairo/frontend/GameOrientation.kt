package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.res.Configuration

/**
 * The screen orientation while a game runs, for all games. Auto leaves rotation to the device;
 * Landscape and Portrait lock it, still turning either way up unless the device's rotation is
 * locked. The library always follows the device.
 *
 * Android reports a rotation but never a refused request, so a launch requests the orientation
 * when it starts ([launching]) and checks it once the game is running ([launched]): preparing
 * and booting the game is the time the rotation has to land. A device setting such as the
 * Retroid's Force landscape keeps the screen as it was, and the player is told.
 */
class GameOrientation(private val activity: Activity, private val preferences: SharedPreferences) {
    private var inGame = false
    private var launch = false
    private var warned = false

    private val selected: String get() = preferences.getString(KEY, AUTO)!!

    fun row() = SettingsRow.choice(KEY, activity.getString(R.string.kairo_orientation_game), ChoiceSpec(activity.getString(R.string.kairo_orientation_game),
        { GameSource.ALL_GAMES }, {
            listOf(ChoiceGroup(null, listOf(ChoiceOption(AUTO, UiText.res(R.string.kairo_auto)),
                ChoiceOption(LANDSCAPE, UiText.res(R.string.kairo_orientation_landscape)),
                ChoiceOption(PORTRAIT, UiText.res(R.string.kairo_orientation_portrait)))))
        }, { selected }, select = { value ->
            preferences.edit().putString(KEY, value).apply()
            apply()
        }))

    /** A launch started: rotate now, while the game is prepared. */
    fun launching() {
        launch = true
        apply()
    }

    /** The launched game is running; say so if the screen kept the other orientation. */
    fun launched() {
        if (!launch) return
        launch = false
        if (!matches()) {
            warned = true
            Ui.message(activity, BLOCKED, long = true)
        }
    }

    /** The launch ended without a game; a running game, if any, keeps its orientation. */
    fun launchFailed() {
        launch = false
        apply()
    }

    fun enterGame() {
        inGame = true
        apply()
    }

    fun leaveGame() {
        inGame = false
        launch = false
        apply()
    }

    /** A rotation that lands after the warning withdraws it. */
    fun configurationChanged() {
        if (warned && matches()) Ui.dismissMessage(activity, BLOCKED)
        if (matches()) warned = false
    }

    private fun matches(): Boolean {
        val wanted = when (selected) {
            LANDSCAPE -> Configuration.ORIENTATION_LANDSCAPE
            PORTRAIT -> Configuration.ORIENTATION_PORTRAIT
            AUTO -> return true
            else -> error("Unknown game orientation: $selected")
        }
        return activity.resources.configuration.orientation == wanted
    }

    private fun apply() {
        activity.requestedOrientation = if (!inGame && !launch) ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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
