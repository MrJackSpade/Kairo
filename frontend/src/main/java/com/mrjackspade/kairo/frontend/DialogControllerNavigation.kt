package com.mrjackspade.kairo.frontend

import android.app.AlertDialog
import android.os.Build
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.widget.Button
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher

/** Dialogs own a separate window, so Activity controller dispatch cannot reach them. */
internal object DialogControllerNavigation {
    private class Callback(val delegate: Window.Callback, val dialog: AlertDialog) : Window.Callback by delegate {
        var backAction: (() -> Unit)? = null
        var systemBack: OnBackInvokedCallback? = null
        val hats = DpadMotionNavigation { dispatchKeyEvent(it) }
        // Plain confirmations have no list/editor to navigate. Own their button focus
        // explicitly; Android can otherwise leave every button unfocused in touch mode.
        fun buttons(): List<Button> {
            if (dialog.listView != null || dialog.findViewById<View>(android.R.id.custom)?.isShown == true)
                return emptyList()
            return listOf(AlertDialog.BUTTON_NEUTRAL, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_POSITIVE)
                .mapNotNull(dialog::getButton).filter { it.visibility == View.VISIBLE && it.isEnabled }
        }
        fun focus(button: Button) {
            button.isFocusableInTouchMode = true
            button.requestFocusFromTouch()
        }
        fun initialFocus() {
            val buttons = buttons()
            val preferred = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.takeIf { it in buttons }
                ?: buttons.firstOrNull() ?: return
            focus(preferred)
        }
        override fun dispatchGenericMotionEvent(event: MotionEvent) = hats.motion(event) || delegate.dispatchGenericMotionEvent(event)
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            val code = when (event.keyCode) {
                KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_DPAD_CENTER
                KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BACK
                else -> event.keyCode
            }
            if (code == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0)
                    backAction?.invoke() ?: dialog.cancel()
                return true
            }
            val buttons = buttons()
            if (buttons.isNotEmpty() && code in setOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER)) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    val selected = buttons.indexOfFirst { it.hasFocus() }
                    if (selected < 0) initialFocus()
                    else when (code) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER ->
                            if (event.repeatCount == 0) buttons[selected].performClick()
                        else -> {
                            val delta = if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_UP) -1 else 1
                            focus(buttons[(selected + delta).coerceIn(0, buttons.lastIndex)])
                        }
                    }
                }
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
        callback.initialFocus()
        window.decorView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit
            override fun onViewDetachedFromWindow(view: View) {
                callback.hats.stop()
                if (Build.VERSION.SDK_INT >= 33) callback.systemBack?.let {
                    dialog.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it)
                    callback.systemBack = null
                }
            }
        })
    }

    fun setBackAction(dialog: AlertDialog, action: () -> Unit) {
        install(dialog)
        val callback = dialog.window?.callback as? Callback ?: return
        callback.backAction = action
        if (Build.VERSION.SDK_INT >= 33 && callback.systemBack == null) {
            callback.systemBack = OnBackInvokedCallback { callback.backAction?.invoke() ?: dialog.cancel() }
            dialog.onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback.systemBack!!)
        }
    }
}
