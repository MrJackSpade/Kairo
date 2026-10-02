package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.MotionEvent
import kotlin.math.abs
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
    private var keyboardGesture = false
    private var tapX = 0f
    private var tapY = 0f
    private var tapMoved = false

    /** Bind to the video AND its full-size background. Child controls retain
     * their own touch targets; empty space around the video remains tappable. */
    fun handleKeyboardTouch(event: MotionEvent, keyboardMode: Boolean): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            keyboardGesture = keyboardMode && inGame() && !secondaryKeyboardVisible()
            tapX = event.x
            tapY = event.y
            tapMoved = false
        }
        if (!keyboardGesture) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> if (abs(event.x - tapX) + abs(event.y - tapY) >
                Ui.dp(activity, 12)) tapMoved = true
            MotionEvent.ACTION_POINTER_DOWN -> tapMoved = true
            MotionEvent.ACTION_UP -> {
                keyboardGesture = false
                if (!tapMoved) showKeyboard()
            }
            MotionEvent.ACTION_CANCEL -> keyboardGesture = false
        }
        return true
    }
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
