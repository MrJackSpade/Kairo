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
            val start = topPage()
            visit(start, listOf(start))
        } finally {
            report.pickers = monitor.hits
            driver.instrumentation.removeMonitor(monitor)
        }
        driver.log.step("crawl", report.toString(), mapOf("pages" to report.pages.joinToString(" | "),
            "skipped" to report.skipped.joinToString(" | ")))
        return report
    }

    /** The name of the page on top (the last one drawn). */
    private fun topPage(): String = checkNotNull(driver.find(By.id(TestAttributes.PAGE)).keyNow()) { "No page is open" }

    private fun visit(page: String, trail: List<String>) {
        if (!seen.add(page)) return
        report.pages += trail.joinToString(" › ")
        driver.find(By.page(page)).waitUntilShown()
        checkPage(page)
        val items = driver.onMain {
            driver.resolveSteps(listOf(Element.Step(By.page(page)), Element.Step(By.anyItem()))).mapNotNull(TestAttributes::item)
        }.distinct()
        for (item in items) {
            val where = "$page › $item"
            if (skip(item)) { report.skipped += where; continue }
            returnTo(page)
            val element = driver.page(page).find(By.item(item))
            if (!element.exists()) continue // Opening an earlier item can remove this one, as a confirm row does.
            val switched = element.keyNow()
            element.tap()
            driver.idle()
            report.items += where
            val top = topPage()
            when {
                driver.find(By.id("modal.button")).exists() -> closeModal()
                top != page -> {
                    visit(top, trail + top)
                    returnTo(page)
                }
                driver.page(page).find(By.id("tray.button")).exists() -> closeTray(page)
                switched == "true" || switched == "false" -> if (element.keyNow() != switched) element.tap()
            }
        }
    }

    /** Presses B until [page] is on top; fails if the stack has lost it. */
    private fun returnTo(page: String) {
        var presses = 0
        while (topPage() != page) {
            // The page may be hidden under the one on top, so ask the stack rather than the screen.
            check(page in driver.openPages()) { "The crawl lost its way: $page is no longer open (on top: ${topPage()})" }
            driver.press(KeyEvent.KEYCODE_BUTTON_B)
            presses++
            check(presses <= 20) { "B did not return to $page" }
        }
    }

    private fun closeModal() {
        val buttons = driver.find(By.id("modal.button")).all()
        (buttons.firstOrNull { it.key() == "Cancel" } ?: buttons.first()).tap()
        driver.find(By.id("modal.button")).waitUntilGone()
    }

    private fun closeTray(page: String) {
        val cancel = driver.page(page).find(By.id("tray.button").withKey("Cancel"))
        if (cancel.exists()) cancel.tap() else driver.press(KeyEvent.KEYCODE_BUTTON_B)
        driver.page(page).find(By.id("tray.button")).waitUntilGone()
    }

    companion object {
        private val OUTSIDE_ACTIONS = listOf(Intent.ACTION_OPEN_DOCUMENT, Intent.ACTION_OPEN_DOCUMENT_TREE,
            Intent.ACTION_GET_CONTENT, Intent.ACTION_CREATE_DOCUMENT, Intent.ACTION_VIEW, Intent.ACTION_SEND)

        private val DESTRUCTIVE = Regex("exit|delete|remove|reset|restart|uninstall|recheck|catalogs|images|clear|" +
            "^slot|^f-library$|^play$|^command$|^layout:|^footer:save$", RegexOption.IGNORE_CASE)

        /** Destructive, slow or network items, and options (picking one changes a setting). */
        fun skipByDefault(item: String): Boolean = DESTRUCTIVE.containsMatchIn(item) || item.startsWith("o:")
    }
}
