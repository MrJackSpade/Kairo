package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.os.Build
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher

/** Routes system and physical Back through the same ordered frontend layers. */
class FrontendBackCoordinator(
    private val activity: Activity,
    private val layers: List<() -> Boolean>,
    private val fallback: () -> Unit
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
        for (layer in layers) if (layer()) return
        fallback()
    }
}
