package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.os.Build
import android.view.View
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher

/** Routes system and physical Back through the same ordered frontend layers. */
class FrontendBackCoordinator(
    private val activity: Activity,
    private val pages: () -> PageStack?,
    private val library: LibraryScreen<*>,
    private val onLibraryRoot: () -> Unit,
    private val keyboard: () -> GuestKeyboardPanel?,
    private val onGameRoot: () -> Unit,
    private val busy: () -> Boolean = { false },
    private val closeKeyboard: () -> Unit = { keyboard()?.close() }
) {
    private val callback = OnBackInvokedCallback { handle() }
    private var registered = false

    fun register() {
        if (Build.VERSION.SDK_INT < 33 || registered) return
        activity.onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
        registered = true
    }

    fun unregister() {
        if (Build.VERSION.SDK_INT < 33 || !registered) return
        activity.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(callback)
        registered = false
    }

    /** The tray, then the page, then the flyout; then the library or the game below them. */
    fun handle() {
        pages()?.takeIf { it.isActive }?.let {
            it.back()
            return
        }
        if (busy()) return
        if (library.visibility == View.VISIBLE) {
            if (library.dismissSearchKeyboard()) return
            if (library.leaveHeader()) return
            onLibraryRoot()
            return
        }
        keyboard()?.takeIf { it.visibility == View.VISIBLE }?.let {
            closeKeyboard()
            return
        }
        onGameRoot()
    }
}
