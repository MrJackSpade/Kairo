package com.mrjackspade.kairo.frontend

/** Keeps guest input and pause state in sync with Android visibility and focus. */
class GuestLifecycleCoordinator(
    private val releaseInputs: () -> Unit,
    private val applyPauseState: () -> Unit,
    private val pauseAudio: () -> Unit,
    private val resumeAudio: () -> Unit
) {
    var isVisible = true
        private set

    fun onPause() = suspendGuest()

    fun onStop() = suspendGuest()

    fun onResume() {
        isVisible = true
        applyPauseState()
        resumeAudio()
    }

    fun onWindowFocusChanged(hasFocus: Boolean) {
        if (!hasFocus) releaseInputs()
    }

    private fun suspendGuest() {
        releaseInputs()
        isVisible = false
        applyPauseState()
        pauseAudio()
    }
}
