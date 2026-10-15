package com.mrjackspade.kairo.frontend

import android.app.Activity

/** Keeps guest input, pause state and fullscreen in sync with Android visibility and focus. */
class GuestLifecycleCoordinator(
    private val activity: Activity,
    /** A game is on screen rather than the library. */
    private val showingGame: () -> Boolean,
    private val releaseInputs: () -> Unit,
    private val applyPauseState: () -> Unit,
    private val pauseAudio: () -> Unit,
    private val resumeAudio: () -> Unit,
    initiallyVisible: Boolean = true,
    private val companionActive: () -> Boolean = { false },
    private val beforeSuspend: () -> Unit = {},
    private val resetGestures: () -> Unit = {},
    private val releaseOnFocusLoss: () -> Unit = releaseInputs
) {
    var isVisible = initiallyVisible
        private set

    fun onPause(): Boolean = suspendGuest()

    fun onStop(): Boolean {
        if (companionActive()) return false
        resetGestures()
        return suspendGuest()
    }

    fun onResume() {
        isVisible = true
        applyPauseState()
        resumeAudio()
        restoreFullscreen()
    }

    fun onWindowFocusChanged(hasFocus: Boolean) {
        if (!hasFocus) releaseOnFocusLoss() else restoreFullscreen()
    }

    /**
     * Android shows the system bars again after a resume, a focus change, a configuration change
     * or a game transition. Gameplay hides them once the window has focus.
     */
    fun restoreFullscreen() {
        ImmersiveWindow.apply(activity)
        if (!showingGame()) return
        activity.window.decorView.post {
            if (!activity.isDestroyed && activity.window.decorView.hasWindowFocus() && showingGame())
                ImmersiveWindow.hideBars(activity)
        }
    }

    private fun suspendGuest(): Boolean {
        if (companionActive()) return false
        beforeSuspend()
        releaseInputs()
        isVisible = false
        applyPauseState()
        pauseAudio()
        return true
    }
}
