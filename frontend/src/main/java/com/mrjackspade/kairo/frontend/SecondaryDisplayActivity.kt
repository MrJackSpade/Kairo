package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher

/** Hosts the secondary panel when Android identifies it as the default display. */
class SecondaryDisplayActivity : Activity() {
    private var owner: SecondaryDisplayCoordinator? = null
    private val backCallback = OnBackInvokedCallback { owner?.forwardBack() }
    private lateinit var content: SecondaryDisplayContent
    private lateinit var viewScale: ViewScale

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = SecondaryDisplayCoordinator.claim(intent)
        if (session == null) { finishAndRemoveTask(); return }
        owner = session
        // System Back bypasses dispatchKeyEvent on Android 13+. Route it to the
        // primary frontend instead of finishing and recreating the keyboard task.
        if (Build.VERSION.SDK_INT >= 33) onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback)
        hideSystemBars()
        viewScale = ViewScale(this)
        showContent(session)
    }

    private fun hideSystemBars() {
        // Focusable, so input that lands on this display always has a window to reach;
        // it is forwarded to the game screen below.
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    private fun showContent(session: SecondaryDisplayCoordinator) {
        if (::content.isInitialized) content.close()
        content = SecondaryDisplayContent(this, session.attachCompanion(this),
            session::forwardGameSurface, session::forwardGameTouch,
            session::currentGameAspect)
        setContentView(content)
        session.updateCompanion(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val session = owner ?: return
        hideSystemBars()
        // Rebuilding the content in place keeps this companion and a swapped game display.
        if (viewScale.changed) {
            showContent(session)
            viewScale.rebuilt()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (owner == null) return
        val displayId = window.decorView.display?.displayId
        if (displayId != Display.DEFAULT_DISPLAY) {
            Log.w(packageName, "Keyboard activity opened on display $displayId instead of 0")
            owner?.releaseCompanion(this)
            finishAndRemoveTask()
        } else Log.i(packageName, "Keyboard activity ready on display 0")
    }

    internal fun setAppearance(showKeyboard: Boolean, color: Int, swapped: Boolean,
                               info: SecondaryDisplayCoordinator.LibraryInfo?) {
        if (::content.isInitialized) content.setAppearance(showKeyboard, color, swapped, info)
    }

    internal fun setInitialMode(touchpad: Boolean) {
        if (::content.isInitialized) content.setInitialMode(touchpad)
    }

    @Deprecated("Legacy Android Back callback")
    override fun onBackPressed() { owner?.forwardBack() }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        owner?.forwardKey(event) ?: super.dispatchKeyEvent(event)

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        owner?.forwardMotion(event) ?: super.dispatchGenericMotionEvent(event)

    internal val activeGameSurface: android.view.SurfaceView?
        get() = if (::content.isInitialized) content.activeGameSurface else null

    override fun onStart() {
        super.onStart()
        owner?.companionStarted(this)
    }

    override fun onStop() {
        owner?.companionStopped(this)
        super.onStop()
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= 33)
            onBackInvokedDispatcher.unregisterOnBackInvokedCallback(backCallback)
        Log.i(packageName, "Keyboard activity closing")
        if (::content.isInitialized) content.close()
        owner?.detachCompanion(this)
        owner = null
        super.onDestroy()
    }
}
