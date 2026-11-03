package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.os.Build
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.annotation.RequiresApi

/**
 * An activity's system Back on Android 13+, where it bypasses key events. Below 13 it does nothing:
 * Back arrives as a key event there.
 *
 * The API 33 types live only in [Callback], which is loaded on Android 13+ alone. A field, lambda or
 * constructor that names them in any other class fails that class on older Android
 * (NoClassDefFoundError at launch on Android 12 and below, Kairo#144).
 */
class SystemBack(private val activity: Activity, private val onBack: () -> Unit) {
    private var callback: Any? = null

    fun register() {
        if (Build.VERSION.SDK_INT < 33 || callback != null) return
        callback = Callback.register(activity, onBack)
    }

    fun unregister() {
        if (Build.VERSION.SDK_INT < 33) return
        val registered = callback ?: return
        Callback.unregister(activity, registered)
        callback = null
    }

    @RequiresApi(33)
    private object Callback {
        fun register(activity: Activity, onBack: () -> Unit): Any =
            OnBackInvokedCallback { onBack() }.also {
                activity.onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, it)
            }

        fun unregister(activity: Activity, callback: Any) =
            activity.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(callback as OnBackInvokedCallback)
    }
}
