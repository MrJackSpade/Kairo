package com.mrjackspade.kairo.frontend

import android.app.AlertDialog
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.Window

/** Dialogs own a separate window, so Activity controller dispatch cannot reach them. */
internal object DialogControllerNavigation {
    private class Callback(val delegate: Window.Callback, val dialog: AlertDialog) : Window.Callback by delegate {
        val hats = DpadMotionNavigation { dispatchKeyEvent(it) }
        override fun dispatchGenericMotionEvent(event: MotionEvent) = hats.motion(event) || delegate.dispatchGenericMotionEvent(event)
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            val code = when (event.keyCode) {
                KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_DPAD_CENTER
                KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BACK
                else -> event.keyCode
            }
            if (code == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) dialog.cancel()
                return true
            }
            val translated = if (code == event.keyCode) event else KeyEvent(event.downTime,
                event.eventTime, event.action, code, event.repeatCount)
            return delegate.dispatchKeyEvent(translated)
        }
    }
    fun install(dialog: AlertDialog) {
        val window = dialog.window ?: return
        if (window.callback is Callback) return
        val callback = Callback(window.callback, dialog)
        window.callback = callback
        window.decorView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit
            override fun onViewDetachedFromWindow(view: View) { callback.hats.stop() }
        })
    }
}
