package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.os.Build
import android.view.View
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher

/** Routes system and physical Back through the same ordered frontend layers. */
class FrontendBackCoordinator(
    private val activity: Activity,
    private val firstRun: FirstRunScreen,
    private val onScreenControls: () -> OnScreenControls?,
    private val controllerEditor: ControllerEditor<*>,
    private val library: LibraryScreen<*>,
    private val onLibraryRoot: () -> Unit,
    private val session: () -> SessionFlow?,
    private val closeSessionMenu: () -> Unit,
    private val keyboard: () -> GuestKeyboardPanel?,
    private val onGameRoot: () -> Unit,
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

    fun handle() {
        if (firstRun.isOpen) {
            firstRun.back()
            return
        }
        onScreenControls()?.takeIf { it.isOpen }?.let {
            it.back()
            return
        }
        if (controllerEditor.isOpen) {
            controllerEditor.back()
            return
        }
        if (library.visibility == View.VISIBLE) {
            if (library.closeDetail()) return
            if (library.closeActions()) return
            onLibraryRoot()
            return
        }
        if (session()?.isOpen == true) {
            closeSessionMenu()
            return
        }
        keyboard()?.takeIf { it.visibility == View.VISIBLE }?.let {
            closeKeyboard()
            return
        }
        onGameRoot()
    }
}
