package com.mrjackspade.kairo.uitest

import android.annotation.SuppressLint
import android.os.Build
import android.view.View
import android.view.inspector.WindowInspector
import androidx.annotation.RequiresApi

/**
 * The root view of every window the app shows, on every display, bottom to top. Main thread only.
 *
 * Android 10+ lists them through WindowInspector. Below 10 (the emulator runs the apps' minSdk,
 * Kairo#114) the same list is WindowManagerGlobal's views, which Espresso reads on those versions too.
 */
internal object WindowRoots {
    fun attached(): List<View> = all().filter { it.isAttachedToWindow }

    fun all(): List<View> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Inspector.views() else beforeQ()

    /** The API 29 class, loaded only on Android 10+ (see frontend SystemBack, Kairo#144). */
    @RequiresApi(Build.VERSION_CODES.Q)
    private object Inspector {
        fun views(): List<View> = WindowInspector.getGlobalWindowViews()
    }

    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    private fun beforeQ(): List<View> {
        val global = Class.forName("android.view.WindowManagerGlobal")
        val instance = global.getMethod("getInstance").invoke(null)
        val views = global.getDeclaredField("mViews").apply { isAccessible = true }.get(instance)
        @Suppress("UNCHECKED_CAST")
        return ArrayList(views as List<View>)
    }
}
