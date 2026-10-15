package com.mrjackspade.kairo.frontend

/** Keeps guest input, pause state and fullscreen in sync with Android visibility and focus. */
class GuestLifecycleCoordinator(
    /** Android shows the system bars again after a resume or focus change; see [GameFullscreen]. */
    private val restoreFullscreen: () -> Unit,
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
