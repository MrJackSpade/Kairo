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

    fun routeToUpper(activity: Activity): Boolean {
        if (!Build.MODEL.equals("RG DS", ignoreCase = true)) return false
        val displays = (activity.getSystemService(Activity.DISPLAY_SERVICE) as DisplayManager).displays
        val upper = displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY &&
            it.isValid && it.state != Display.STATE_OFF } ?: return false
        @Suppress("DEPRECATION")
        val currentId = activity.display?.displayId
            ?: activity.windowManager.defaultDisplay.displayId
        if (currentId == upper.displayId) return false
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
            activity.finish()
            true
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}
