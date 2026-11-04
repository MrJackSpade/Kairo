package com.mrjackspade.kairo.uitest

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.view.KeyEvent
import com.mrjackspade.kairo.frontend.TestAttributes

/**
 * Walks every page reachable from the page on top, as a player exploring the menus would: it opens
 * each focusable item in turn, follows every page that opens (each page once), closes trays and
 * modals with their Cancel, puts back any switch it flipped, and returns with the controller's B.
 * It never picks an option, never uses a file picker or link (they are answered as cancelled), and
 * skips items whose id names something destructive or slow ([skip]). Each page is checked as it is
 * reached ([checkPage]); the [Report] lists everything it opened and skipped.
 */
class MenuCrawler(
    private val driver: Driver,
    private val skip: (String) -> Boolean = ::skipByDefault,
    private val checkPage: (String) -> Unit = {}
) {
    class Report {
        val pages = ArrayList<String>()
        val items = ArrayList<String>()
        val skipped = ArrayList<String>()
        var pickers = 0
        override fun toString() = "${pages.size} pages, ${items.size} items, ${skipped.size} skipped, $pickers pickers"
    }

    private val report = Report()
    private val seen = HashSet<String>()

    /** Crawls from the page on top, and returns there. */
    fun crawl(): Report {
        // Matched by action whatever the intent's MIME type: an IntentFilter without types never
        // matches a picker that names one, such as the catalog import's */*.
        val cancelled = Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? = cancelled.takeIf {
                intent.action in OUTSIDE_ACTIONS &&
                    (intent.action != Intent.ACTION_VIEW || intent.scheme == "http" || intent.scheme == "https")
            }
        }
        driver.instrumentation.addMonitor(monitor)
        try {
            val start = stack()
            visit(start.size, listOf(start.last()))
        } finally {
            report.pickers = monitor.hits
            driver.instrumentation.removeMonitor(monitor)
        }
        driver.log.step("crawl", report.toString(), mapOf("pages" to report.pages.joinToString(" | "),
            "skipped" to report.skipped.joinToString(" | ")))
        return report
    }

    /**
     * The main screen's open pages by name, bottom to top (drawing order). Pages are told apart by
     * their place here, not by name: a choice's page is named after its setting, which can be the
     * name of the page it opens from (KairoMSX's Machine setting on the Machine page, Kairo#172).
     */
    private fun stack(): List<String> = driver.onMain {
        // Every attached page, shown or not: the stack hides the pages under the one on top.
        val page = By.id(TestAttributes.PAGE).notInside(By.LOWER_SCREEN)
        WindowRoots.attached().flatMap { Views.descendants(it) }.filter(page::matches)
            .map { checkNotNull(TestAttributes.key(it)) { "A page has no name" } }.toList()
    }.also { check(it.isNotEmpty()) { "No page is open" } }

    /** The page on top, as the last shown page of its name: the pages under it are hidden. */
    private fun topStep(): Element.Step {
        val by = By.mainPage(stack().last())
        val shown = driver.onMain { driver.resolveSteps(listOf(Element.Step(by))).size }
        check(shown > 0) { "The page on top is not shown" }
        return Element.Step(by, shown - 1)
    }

    /** Crawls the page on top, the [depth]-th open page, reached through [trail]. */
    private fun visit(depth: Int, trail: List<String>) {
        val page = trail.last()
        // A page is crawled once, wherever it opens from; one named as a page below it is another page.
        if (!seen.add(if (page in trail.dropLast(1)) trail.joinToString(" › ") else page)) return
        report.pages += trail.joinToString(" › ")
        val below = stack().take(depth)
        check(below.size == depth) { "The crawl lost its way: ${below.size} pages open, not $depth" }
        Element(driver, listOf(Element.Step(By.mainPage(page)))).waitUntilShown()
        checkPage(page)
        val top = topStep()
        val items = driver.onMain {
            driver.resolveSteps(listOf(top, Element.Step(By.anyItem()))).mapNotNull(TestAttributes::item)
        }.distinct()
        for (item in items) {
            val where = "$page › $item"
            if (skip(item)) { report.skipped += where; continue }
            returnTo(below)
            val step = topStep()
            val element = Element(driver, listOf(step, Element.Step(By.item(item))))
            if (!element.exists()) continue // Opening an earlier item can remove this one, as a confirm row does.
            val switched = element.keyNow()
            element.tap()
            driver.idle()
            // A page the tap opened is off screen until it slides in, and so not yet on the stack.
            driver.awaitPagesSettled()
            report.items += where
            val now = stack()
            val tray = Element(driver, listOf(step, Element.Step(By.id("tray.button"))))
            when {
                driver.find(By.id("modal.button")).exists() -> closeModal()
                now.size > depth -> {
                    visit(now.size, trail + now.last())
                    returnTo(below)
                }
                tray.exists() -> closeTray(step)
                switched == "true" || switched == "false" -> if (element.keyNow() != switched) element.tap()
            }
        }
    }

    /** Presses B until the pages [below] are the whole stack again; fails if the stack has lost them. */
    private fun returnTo(below: List<String>) {
        var presses = 0
        while (true) {
            val now = stack()
            // The page may be hidden under the one on top, so ask the stack rather than the screen.
            check(now.take(below.size) == below) { "The crawl lost its way: open pages ${now.joinToString(" › ")}, " +
                "not above ${below.joinToString(" › ")}" }
            if (now.size == below.size) return
            driver.press(KeyEvent.KEYCODE_BUTTON_B)
            // The page closes as it slides away: one B at a time, or a slow close takes several.
            driver.waitUntil("B to close ${now.last()}") { stack().size < now.size }
            driver.awaitPagesSettled()
            presses++
            check(presses <= 20) { "B did not return to ${below.last()}" }
        }
    }

    private fun closeModal() {
        val buttons = driver.find(By.id("modal.button")).all()
        (buttons.firstOrNull { it.key() == "Cancel" } ?: buttons.first()).tap()
        driver.find(By.id("modal.button")).waitUntilGone()
    }

    private fun closeTray(page: Element.Step) {
        val cancel = Element(driver, listOf(page, Element.Step(By.id("tray.button").withKey("Cancel"))))
        if (cancel.exists()) cancel.tap() else driver.press(KeyEvent.KEYCODE_BUTTON_B)
        Element(driver, listOf(page, Element.Step(By.id("tray.button")))).waitUntilGone()
    }

    companion object {
        private val OUTSIDE_ACTIONS = listOf(Intent.ACTION_OPEN_DOCUMENT, Intent.ACTION_OPEN_DOCUMENT_TREE,
            Intent.ACTION_GET_CONTENT, Intent.ACTION_CREATE_DOCUMENT, Intent.ACTION_VIEW, Intent.ACTION_SEND)

        private val DESTRUCTIVE = Regex("exit|delete|remove|reset|restart|uninstall|recheck|catalogs|images|clear|" +
            "^slot|^f-library$|^play$|^command$|^layout:|^footer:save$|^done$", RegexOption.IGNORE_CASE)

        /**
         * Destructive, slow or network items, options (picking one changes a setting: a choice's o:
         * rows and a scaling anchor's a: squares) and the buttons that commit a page's changes and
         * close it (a mapping's Save, an arrangement's Done). A pick closes its page a moment later
         * (ScalingAnchor.pick), so a crawl that picked raced that close.
         */
        fun skipByDefault(item: String): Boolean =
            DESTRUCTIVE.containsMatchIn(item) || item.startsWith("o:") || item.startsWith("a:")
    }
}
