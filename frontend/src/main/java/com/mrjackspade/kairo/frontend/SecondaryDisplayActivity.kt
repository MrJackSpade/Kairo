package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

/** Hosts the secondary panel when Android identifies it as the default display. */
class SecondaryDisplayActivity : Activity() {
    private var owner: SecondaryDisplayCoordinator? = null
    private lateinit var content: SecondaryDisplayContent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = SecondaryDisplayCoordinator.pendingCompanion
        if (session == null) { finish(); return }
        owner = session
        // Focusable, so input that lands on this display always has a window to reach;
        // it is forwarded to the game screen below.
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        content = SecondaryDisplayContent(this, session.attachCompanion(this),
            session::forwardGameSurface, session::forwardGameTouch,
            session::currentGameAspect)
        setContentView(content)
        session.updateCompanion(this)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (owner == null) return
        val displayId = window.decorView.display?.displayId
        if (displayId != Display.DEFAULT_DISPLAY) {
            Log.w(packageName, "Keyboard activity opened on display $displayId instead of 0")
            finish()
        } else Log.i(packageName, "Keyboard activity ready on display 0")
    }

    internal fun setAppearance(showKeyboard: Boolean, color: Int, swapped: Boolean,
                               info: SecondaryDisplayCoordinator.LibraryInfo?) {
        if (::content.isInitialized) content.setAppearance(showKeyboard, color, swapped, info)
    }

    internal fun setInitialMode(touchpad: Boolean) {
        if (::content.isInitialized) content.setInitialMode(touchpad)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        owner?.forwardKey(event) ?: super.dispatchKeyEvent(event)

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        owner?.forwardMotion(event) ?: super.dispatchGenericMotionEvent(event)

    internal val activeGameSurface: android.view.SurfaceView?
        get() = if (::content.isInitialized) content.activeGameSurface else null

    override fun onDestroy() {
        Log.i(packageName, "Keyboard activity closing")
        if (::content.isInitialized) content.close()
        owner?.detachCompanion(this)
        owner = null
        super.onDestroy()
    }
}
