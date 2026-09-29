package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display

/** Starts the main frontend on the RGDS upper display, leaving display 0 for input. */
object RgDsDisplayRouter {
    private const val REDIRECTED = "com.mrjackspade.kairo.frontend.displayRedirected"

    fun routeToUpper(activity: Activity): Boolean {
        if (!Build.MODEL.equals("RG DS", ignoreCase = true) ||
            activity.intent.getBooleanExtra(REDIRECTED, false)) return false
        val displays = (activity.getSystemService(Activity.DISPLAY_SERVICE) as DisplayManager).displays
        val upper = displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY &&
            it.isValid && it.state != Display.STATE_OFF } ?: return false
        @Suppress("DEPRECATION")
        val currentId = activity.windowManager.defaultDisplay.displayId
        if (currentId == upper.displayId) return false
        return try {
            val redirected = Intent(activity.intent).apply {
                setClass(activity, activity.javaClass)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                putExtra(REDIRECTED, true)
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
