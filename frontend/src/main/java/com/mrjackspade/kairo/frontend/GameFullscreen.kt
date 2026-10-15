package com.mrjackspade.kairo.frontend

import android.app.Activity

/**
 * Android shows the system bars again after a resume, a focus change, a configuration change or
 * a game transition. This restores fullscreen, and hides the bars during gameplay once the
 * window has focus.
 */
class GameFullscreen(private val activity: Activity, private val showingGame: () -> Boolean) {
    fun restore() {
        ImmersiveWindow.apply(activity)
        if (!showingGame()) return
        activity.window.decorView.post {
            if (!activity.isDestroyed && activity.window.decorView.hasWindowFocus() && showingGame())
                ImmersiveWindow.hideBars(activity)
        }
    }
}
