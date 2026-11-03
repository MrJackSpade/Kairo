package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.view.View

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
    private val systemBack = SystemBack(activity) { handle() }

    fun register() = systemBack.register()

    fun unregister() = systemBack.unregister()

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
