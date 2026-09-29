package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsets

/** Keeps the guest keyboard, Android IME, and touch controls mutually exclusive. */
class TouchUiCoordinator(
    private val activity: Activity,
    private val library: LibraryScreen<*>,
    private val inGame: () -> Boolean,
    private val secondaryKeyboardVisible: () -> Boolean,
    private val onLayout: () -> Unit,
    private val onVisibility: () -> Unit,
    private val onClosed: () -> Unit = {}
) {
    private var keyboard: GuestKeyboardPanel? = null
    private var openGeneration = 0
    private val visibilityListener: () -> Unit = {
        onLayout()
        onVisibility()
    }

    fun bind(panel: GuestKeyboardPanel?) {
        keyboard?.removeVisibilityListener(visibilityListener)
        keyboard = panel
        panel?.addVisibilityListener(visibilityListener)
    }

    fun showKeyboard(): Boolean {
        val panel = keyboard ?: return false
        if (!inGame() || secondaryKeyboardVisible() || panel.visibility == View.VISIBLE)
            return false
        library.dismissSystemKeyboard()
        val generation = ++openGeneration
        fun openWhenImeClosed(attempts: Int) {
            if (generation != openGeneration || !inGame() || secondaryKeyboardVisible()) return
            val imeVisible = Build.VERSION.SDK_INT >= 30 &&
                activity.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true
            if (imeVisible) {
                if (attempts > 0) panel.postDelayed({ openWhenImeClosed(attempts - 1) }, 40)
                return
            }
            panel.open()
            panel.postDelayed({
                if (panel.visibility == View.VISIBLE && inGame()) ImmersiveWindow.hideBars(activity)
            }, 350)
        }
        openWhenImeClosed(20)
        return true
    }

    fun hideKeyboard() {
        openGeneration++
        val panel = keyboard
        if (panel?.visibility == View.VISIBLE) panel.close()
        else {
            onLayout()
            onVisibility()
        }
        onClosed()
    }

    fun refreshControls(controls: OnScreenControls?, playing: Boolean, swapped: Boolean) {
        controls?.refreshVisibility(playing && !swapped &&
            keyboard?.visibility != View.VISIBLE)
    }
}
