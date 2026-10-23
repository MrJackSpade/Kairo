package com.mrjackspade.kairo.uitest

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.mrjackspade.kairo.frontend.PixelTextView

/** Reading views as a player sees them. Every function runs on the main thread. */
object Views {
    /** The text a view shows itself, or null for one that shows none. */
    fun ownText(view: View): String? = when (view) {
        is TextView -> view.text?.toString()
        is PixelTextView -> view.text
        else -> null
    }

    /** Every text the view and its present descendants show, in drawing order. */
    fun texts(view: View): List<String> = descendants(view).filter(::present).mapNotNull(::ownText).filter { it.isNotEmpty() }.toList()

    /** The view and every view below it, depth first. */
    fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }

    /**
     * Part of the page the player has: attached, visible with every ancestor, not faded out, and
     * laid out with some size. It may be scrolled out of view, as Selenium's present elements may.
     */
    fun present(view: View): Boolean {
        if (!view.isAttachedToWindow || !view.isShown || view.width == 0 || view.height == 0) return false
        var ancestor: View? = view
        while (ancestor != null) {
            if (ancestor.alpha == 0f) return false
            ancestor = ancestor.parent as? View
        }
        return true
    }

    /** Present and at least partly on screen now, where a touch can reach it. */
    fun onScreen(view: View): Boolean = present(view) && view.getGlobalVisibleRect(Rect())

    /** Where the view is now, in its window. */
    fun boundsInWindow(view: View): Rect {
        val origin = IntArray(2)
        view.getLocationInWindow(origin)
        return Rect(origin[0], origin[1], origin[0] + view.width, origin[1] + view.height)
    }

    /** A short description for the log: the view's test id and key, class, and text. */
    fun describe(view: View): String {
        val id = com.mrjackspade.kairo.frontend.TestAttributes.id(view)
        val key = com.mrjackspade.kairo.frontend.TestAttributes.key(view)
        val text = ownText(view)?.take(60)
        return listOfNotNull(view.javaClass.simpleName.ifEmpty { "View" }, id?.let { "id=$it" }, key?.let { "key=$it" },
            text?.let { "\"$it\"" }).joinToString(" ")
    }
}
