package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display

/** Starts the main frontend on the RGDS upper display, leaving display 0 for input. */
object RgDsDisplayRouter {
    private const val REDIRECT_ATTEMPTS = "com.mrjackspade.kairo.frontend.displayRedirectAttempts"
    private const val MAX_REDIRECT_ATTEMPTS = 3
    private const val RETURN_TO_UPPER = "com.mrjackspade.kairo.frontend.returnToUpper"

    private fun misplacedUpper(activity: Activity): Display? {
        if (!Build.MODEL.equals("RG DS", ignoreCase = true)) return null
        val displays = (activity.getSystemService(Activity.DISPLAY_SERVICE) as DisplayManager).displays
        val upper = displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY &&
            it.isValid && it.state != Display.STATE_OFF } ?: return null
        @Suppress("DEPRECATION")
        val currentId = activity.display?.displayId
            ?: activity.windowManager.defaultDisplay.displayId
        return upper.takeIf { currentId != it.displayId }
    }

    /*
     * Reopening the app from the lower screen's launcher or recents moves its running task
     * onto the lower display. Relaunching into the same task with the upper display as its
     * target moves it back without recreating it; the game keeps running.
     */
    fun returnToUpper(activity: Activity): Boolean {
        val upper = misplacedUpper(activity) ?: return false
        val intent = Intent(activity, activity.javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(RETURN_TO_UPPER, true)
        }
        activity.startActivity(intent, ActivityOptions.makeBasic().setLaunchDisplayId(upper.displayId).toBundle())
        return true
    }

    /** The intent [returnToUpper] delivers to the running activity carries no request. */
    fun isReturnToUpper(intent: Intent): Boolean = intent.getBooleanExtra(RETURN_TO_UPPER, false)

    fun routeToUpper(activity: Activity): Boolean {
        val upper = misplacedUpper(activity) ?: return false
        // Some RGDS firmware launches a redirected activity on the wrong display.
        // Check its actual display and retry a bounded number of times.
        val attempts = activity.intent.getIntExtra(REDIRECT_ATTEMPTS, 0)
        if (attempts >= MAX_REDIRECT_ATTEMPTS) return false
        return try {
            val redirected = Intent(activity.intent).apply {
                setClass(activity, activity.javaClass)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                setSourceBounds(null)
                putExtra(REDIRECT_ATTEMPTS, attempts + 1)
            }
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(upper.displayId)
            activity.startActivity(redirected, options.toBundle())
            // The lower-display task would stay in recents and reopen a second instance.
            activity.finishAndRemoveTask()
            true
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}
