package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.view.KeyEvent

/** Activity-owned state shared by embedded setup pages and the controller dialog. */
internal class FirstRunVisibility private constructor() {
    private val screens = mutableSetOf<FirstRunScreen>()
    private val observers = mutableSetOf<(Boolean) -> Unit>()

    fun setVisible(screen: FirstRunScreen, visible: Boolean) {
        val wasVisible = screens.isNotEmpty()
        if (visible) screens.add(screen) else screens.remove(screen)
        if (wasVisible != screens.isNotEmpty())
            observers.toList().forEach { it(screens.isNotEmpty()) }
    }

    fun handleKey(event: KeyEvent): Boolean? = screens.lastOrNull()?.handleKey(event)

    fun observe(observer: (Boolean) -> Unit): () -> Unit {
        observers.add(observer)
        observer(screens.isNotEmpty())
        return { observers.remove(observer) }
    }

    companion object {
        fun forActivity(activity: Activity): FirstRunVisibility {
            val decor = activity.window.decorView
            return decor.getTag(R.id.kairo_first_run_visibility) as? FirstRunVisibility
                ?: FirstRunVisibility().also { decor.setTag(R.id.kairo_first_run_visibility, it) }
        }
    }
}
