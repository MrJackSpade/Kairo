package com.mrjackspade.kairo.uitest

import android.view.View
import android.widget.AbsListView
import android.widget.ScrollView
import com.mrjackspade.kairo.frontend.TestAttributes

/**
 * A selector path to an element, like a Selenium WebElement except that it holds no view: every
 * action looks the element up again, so it always acts on what is on screen now. With no [index],
 * it is the topmost match (the one drawn last); [nth] picks one by drawing order.
 */
class Element internal constructor(private val driver: Driver, private val path: List<By>, private val index: Int? = null) {
    val description: String = path.joinToString(" › ") + (index?.let { " [#$it]" } ?: "")

    /** An element inside this one. */
    fun find(by: By) = Element(driver, path + by)

    /** The [n]th match in drawing order, counting from 0. */
    fun nth(n: Int) = Element(driver, path, n)

    private fun current(): View? {
        val matches = driver.resolve(path)
        return if (index == null) matches.lastOrNull() else matches.getOrNull(index)
    }

    /** Whether it is on screen now, without waiting. */
    fun exists(): Boolean = driver.onMain { current() != null }

    /** How many views match now, without waiting. */
    fun count(): Int = driver.onMain { driver.resolve(path).size }

    /** Each match now, in drawing order, as its own element. */
    fun all(): List<Element> = (0 until count()).map(::nth)

    /** Waits until it is on screen and has stopped moving, as after a page slides in. */
    fun waitUntilShown(): Element {
        driver.waitUntil("$description to be shown", ::exists)
        var last = boundsNow()
        driver.waitUntil("$description to stop moving") {
            val now = boundsNow()
            (now == last).also { last = now }
        }
        return this
    }

    fun waitUntilGone(): Element {
        driver.waitUntil("$description to be gone") { !exists() }
        return this
    }

    private fun boundsNow() = driver.onMain { current()?.let(Views::boundsInWindow) }

    /** The text it shows itself now, without waiting; null when it is not on screen. */
    fun textNow(): String? = driver.onMain { current()?.let(Views::ownText) }

    /** Its key now, without waiting; null when it is not on screen or has none. */
    fun keyNow(): String? = driver.onMain { current()?.let(TestAttributes::key) }

    /** Touches it where it is now, scrolling it into view first when its page has scrolled it away. */
    fun tap(): Element {
        waitUntilShown()
        if (!driver.onMain { current()?.let(Views::onScreen) == true }) {
            driver.log.step("scroll", "$description into view")
            driver.onMain { current()?.let { it.requestRectangleOnScreen(android.graphics.Rect(0, 0, it.width, it.height), true) } }
            driver.waitUntil("$description to be on screen") { driver.onMain { current()?.let(Views::onScreen) == true } }
            waitUntilShown()
        }
        driver.onMain {
            val view = checkNotNull(current()) { "$description went away before the tap" }
            driver.log.step("tap", description, mapOf("view" to Views.describe(view), "bounds" to Views.boundsInWindow(view).toShortString()))
            driver.tapNow(view)
        }
        driver.idle()
        return this
    }

    /** Touches it to focus it, then types [text] through the system's input path. */
    fun type(text: String): Element {
        tap()
        driver.waitUntil("$description to take focus") { driver.onMain { current()?.let { it.isFocused || it.hasFocus() } == true } }
        driver.type(text)
        return this
    }

    /** The text it shows itself, or every text inside it joined by " | " for a container. */
    fun text(): String = withView { view -> Views.ownText(view) ?: Views.texts(view).joinToString(" | ") }

    /** Every text shown inside it. */
    fun texts(): List<String> = withView(Views::texts)

    /** Its test key, such as a game's entry id. */
    fun key(): String? = withView(TestAttributes::key)

    private fun <T> withView(read: (View) -> T): T {
        waitUntilShown()
        return driver.onMain { read(checkNotNull(current()) { "$description went away" }) }
    }

    /**
     * Brings it into view inside the scrolling [container], such as the library list, which keeps
     * views only for the rows on screen: scrolls from the top, a screen at a time, until it is
     * there; fails at the end of the list.
     */
    fun revealIn(container: Element): Element {
        if (exists()) return this
        driver.log.step("scroll", "$description in ${container.description}")
        container.waitUntilShown()
        driver.onMain { scroll(container, toTop = true) }
        while (!exists()) {
            val moved = driver.onMain { scroll(container, toTop = false) }
            driver.idle()
            if (!moved && !exists()) throw AssertionError("$description is not in ${container.description}")
        }
        return this
    }

    /** Scrolls the container to its top, or a screen down; false when it could go no further. */
    private fun scroll(container: Element, toTop: Boolean): Boolean {
        val view = checkNotNull(container.current()) { "${container.description} went away" }
        return when (view) {
            is AbsListView -> if (toTop) { view.setSelection(0); true }
                else view.canScrollList(1).also { if (it) view.scrollListBy(view.height * 4 / 5) }
            is ScrollView -> if (toTop) { view.scrollTo(0, 0); true }
                else view.canScrollVertically(1).also { if (it) view.scrollBy(0, view.height * 4 / 5) }
            else -> error("${container.description} does not scroll (${view.javaClass.simpleName})")
        }
    }

    override fun toString() = description
}
