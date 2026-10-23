package com.mrjackspade.kairo.uitest

import android.view.View
import android.widget.AbsListView
import android.widget.ScrollView
import com.mrjackspade.kairo.frontend.TestAttributes

/**
 * A selector path to an element, like a Selenium WebElement except that it holds no view: every
 * action looks the element up again, so it always acts on what is on screen now. Each step of the
 * path searches inside what the step before found; a step with an index keeps only that match (in
 * drawing order), and the element is the topmost of what the last step finds.
 */
class Element internal constructor(private val driver: Driver, private val path: List<Step>) {
    /** One selector of the path, and which of its matches to keep: null keeps every one. */
    class Step(val by: By, val index: Int? = null) {
        override fun toString() = by.description + (index?.let { " [#$it]" } ?: "")
    }

    val description: String = path.joinToString(" › ")

    /** An element inside this one. */
    fun find(by: By) = Element(driver, path + Step(by))

    /** The [n]th match of this element's selector in drawing order, counting from 0. */
    fun nth(n: Int) = Element(driver, path.dropLast(1) + Step(path.last().by, n))

    private fun current(): View? = driver.resolveSteps(path).lastOrNull()

    /** Whether it is on screen now, without waiting. */
    fun exists(): Boolean = driver.onMain { current() != null }

    /** How many views match now, without waiting. */
    fun count(): Int = driver.onMain { driver.resolveSteps(path.dropLast(1) + Step(path.last().by)).size }

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

    /** Where it is in its window now, without waiting; null when it is not on screen. */
    fun boundsInWindowNow(): android.graphics.Rect? = boundsNow()

    /** Whether all of it is on screen now, cut off by neither a scrolling page nor the screen's edge. */
    fun fullyOnScreenNow(): Boolean = driver.onMain {
        current()?.let { view ->
            val visible = android.graphics.Rect()
            Views.present(view) && view.getGlobalVisibleRect(visible) &&
                visible.width() == view.width && visible.height() == view.height
        } == true
    }

    /** The text it shows itself now, without waiting; null when it is not on screen. */
    fun textNow(): String? = driver.onMain { current()?.let(Views::ownText) }

    /** Its navigation id as a focusable item (TestAttributes.item); fails for a view that is not one. */
    fun itemId(): String = withView { view -> checkNotNull(TestAttributes.item(view)) { "$description is not a focusable item" } }

    /** Its key now, without waiting; null when it is not on screen or has none. */
    fun keyNow(): String? = driver.onMain { current()?.let(TestAttributes::key) }

    /** Touches its centre, scrolling it into view first when its page has scrolled it (partly) away. */
    fun tap(): Element {
        waitUntilShown()
        // A page still sliding in or out can take the touch meant for this view.
        driver.waitUntil("the pages to settle") { !driver.find(By.id("pages").withKey("moving")).exists() }
        if (!driver.onMain { current()?.let(Views::tappable) == true }) {
            driver.log.step("scroll", "$description into view")
            driver.onMain { current()?.let { it.requestRectangleOnScreen(android.graphics.Rect(0, 0, it.width, it.height), true) } }
            driver.waitUntil("$description to be on screen") { driver.onMain { current()?.let(Views::tappable) == true } }
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

    /** Presses it at its centre for [millis], as a finger held on an on-screen button. */
    fun hold(millis: Long): Element = drag(0.5f, 0.5f, 0.5f, 0.5f, steps = 1, stepMillis = millis)

    /**
     * Drags a finger across it: down at ([fromX], [fromY]) and up at ([toX], [toY]), as fractions
     * of its width and height (beyond 0..1 leaves it, as pushing a stick past its rim does), in
     * [steps] moves [stepMillis] apart, holding [holdMillis] at the end before lifting. Every event
     * reads the element's position again, so the gesture follows it if it moves.
     */
    fun drag(fromX: Float, fromY: Float, toX: Float, toY: Float, steps: Int = 12, stepMillis: Long = 16,
             holdMillis: Long = 0): Element {
        waitUntilShown()
        driver.log.step("drag", description, mapOf("from" to "$fromX,$fromY", "to" to "$toX,$toY", "steps" to steps))
        val down = android.os.SystemClock.uptimeMillis()
        touch(android.view.MotionEvent.ACTION_DOWN, down, fromX, fromY)
        for (step in 1..steps) {
            Thread.sleep(stepMillis)
            val fraction = step.toFloat() / steps
            touch(android.view.MotionEvent.ACTION_MOVE, down, fromX + (toX - fromX) * fraction, fromY + (toY - fromY) * fraction)
        }
        if (holdMillis > 0) Thread.sleep(holdMillis)
        touch(android.view.MotionEvent.ACTION_UP, down, toX, toY)
        driver.idle()
        return this
    }

    /** One touch event at a fraction of its current bounds, dispatched to its window. */
    private fun touch(action: Int, downTime: Long, fractionX: Float, fractionY: Float) = driver.onMain {
        val view = checkNotNull(current()) { "$description went away during a touch" }
        val bounds = Views.boundsInWindow(view)
        val event = android.view.MotionEvent.obtain(downTime, android.os.SystemClock.uptimeMillis(), action,
            bounds.left + bounds.width() * fractionX, bounds.top + bounds.height() * fractionY, 0)
        event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
        try { view.rootView.dispatchTouchEvent(event) } finally { event.recycle() }
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
