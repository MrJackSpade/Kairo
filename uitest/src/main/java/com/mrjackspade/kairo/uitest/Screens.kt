package com.mrjackspade.kairo.uitest

import android.view.KeyEvent

/**
 * The app's screens as page objects: each method is something a player does or sees. They use
 * only selectors, touches and controller input (Driver), never the app's internals.
 */
class Screens(val driver: Driver, val app: AppUnderTest, val documents: TestDocuments) {
    val library = Library(driver)

    /** The setup page on top, by its title, such as "Choose a games folder". */
    fun setupPage(title: String) = driver.page(title)

    /**
     * Goes through first run as a player would: the controller layout when asked, the games
     * folder ([games], a folder of the test resources), then the BIOS step when the app has one,
     * filled from the test resources' BIOS folder (AppUnderTest.biosResource) by its folder import
     * when [importBios] is set and the tests have one.
     * [onPage] runs with each setup page's title once it is on screen, for tests that check them.
     */
    fun completeFirstRun(games: String = "games", importBios: Boolean = true, onPage: (String) -> Unit = {}): Library {
        val layout = driver.page("Controller layout")
        val folder = setupPage(app.folderStepTitle)
        driver.waitUntil("a setup page") { layout.exists() || folder.exists() }
        if (layout.exists()) {
            layout.waitUntilShown()
            onPage("Controller layout")
            val auto = layout.find(By.id("layout:AUTO"))
            (if (auto.exists()) auto else layout.find(By.id("layout:WITH_STICKS"))).tap()
            layout.find(By.id("footer:continue")).tap()
        }
        folder.waitUntilShown()
        onPage(app.folderStepTitle)
        documents.answerFolder(documents.folder(games)) { folder.find(By.id("footer:choose")).tap() }
        app.biosTitle?.let { title ->
            val step = setupPage(title).waitUntilShown()
            onPage(title)
            app.biosResource?.takeIf { importBios }?.let { bios -> importBiosFolder(step, bios) }
            step.find(By.id("footer:done")).tap()
        }
        return library.waitUntilShown()
    }

    /** A BIOS file's row on a BIOS page, by its file name (row id "firmware<set>-<file>"). */
    fun biosRow(page: Element, file: String) =
        page.find(By.idMatching(Regex("firmware\\d+-" + Regex.escape(file))))

    /** Imports the test resources' [bios] folder from a BIOS page's folder row, and waits for it. */
    fun importBiosFolder(page: Element, bios: String = checkNotNull(app.biosResource) { "${app.name}'s tests have no BIOS files" }) {
        documents.answerFolder(documents.folder(bios)) { page.find(By.id("firmware-folder")).tap() }
        driver.waitUntil("every required BIOS file to be installed") {
            app.biosFiles.filter { it.required }.all { biosRow(page, it.file).find(By.id("value")).textNow() == "Installed" }
        }
    }

    /** The Machine page, from the library menu: machine defaults and the BIOS rows. */
    fun openMachinePage(): Element {
        library.openMenu().find(By.id("f-machine")).tap()
        return driver.page("Machine").waitUntilShown()
    }

    /** Opens the in-game menu, as the controller's menu button does. */
    fun openGameMenu(): Element {
        driver.press(KeyEvent.KEYCODE_BUTTON_MODE)
        return driver.page("flyout").waitUntilShown()
    }

    /** Leaves the running game for the library through the menu's Exit, confirming it. */
    fun exitGame(): Library {
        openGameMenu().find(By.id("f-exit")).tap()
        // The confirmation is a modal titled by its question; its buttons are named by label.
        driver.find(By.id("modal.button")).waitUntilShown()
        driver.find(By.id("modal.button")).all().first { it.key() != "Cancel" }.tap()
        return library.waitUntilShown()
    }
}

/** The library: the header, search, the game list, and its menu. */
class Library(private val driver: Driver) {
    val brand = driver.find(By.id("library.brand"))
    val list = driver.find(By.id("library.list"))
    private val scanning = driver.find(By.id("library.scanning"))

    fun waitUntilShown(): Library {
        brand.waitUntilShown()
        return this
    }

    /** Waits until no scan runs and [title] is listed, so the list is complete and the catalog read. */
    fun waitForGame(title: String): Library {
        driver.waitUntil("the library to list \"$title\"") { !scanning.exists() && reveal(title) }
        return this
    }

    /** The entry for [title], brought into view. */
    fun entry(title: String): Element {
        waitForGame(title)
        return driver.find(By.id("library.entry").withText(title))
    }

    /** Every entry, top to bottom, as (key, title, file line); scrolls through the whole list. */
    fun entries(): List<Entry> {
        val seen = LinkedHashMap<String, Entry>()
        driver.onMain { driver.resolve(listOf(By.id("library.list"))).lastOrNull()?.let { (it as android.widget.AbsListView).setSelection(0) } }
        driver.idle()
        while (true) {
            driver.find(By.id("library.entry")).all().forEach { row ->
                val key = row.keyNow() ?: return@forEach
                seen.getOrPut(key) { Entry(key, row.find(By.id("title")).text(), row.find(By.id("detail")).text()) }
            }
            val moved = driver.onMain {
                val view = driver.resolve(listOf(By.id("library.list"))).last() as android.widget.AbsListView
                view.canScrollList(1).also { if (it) view.scrollListBy(view.height * 4 / 5) }
            }
            driver.idle()
            if (!moved) break
        }
        driver.log.step("entries", "${seen.size} entries", mapOf("titles" to seen.values.joinToString(" | ") { it.title }))
        return seen.values.toList()
    }

    class Entry(val key: String, val title: String, val file: String)

    private fun reveal(title: String): Boolean {
        val entry = driver.find(By.id("library.entry").withText(title))
        if (entry.exists()) return true
        return try { entry.revealIn(list); true } catch (_: AssertionError) { false }
    }

    /** Opens [title]'s game page. */
    fun open(title: String): GamePage {
        entry(title).tap()
        return GamePage(driver).also { it.page.waitUntilShown() }
    }

    fun openMenu(): Element {
        driver.find(By.id("library.menu")).tap()
        return driver.page("flyout").waitUntilShown()
    }

    /** Opens search and types [term] in whichever field takes it (the lower screen's on a dual-screen device). */
    fun search(term: String) {
        driver.find(By.id("library.search")).tap()
        val lower = driver.find(By.id("secondary.search.field"))
        val field = if (lower.exists()) lower else driver.find(By.id("library.search.field"))
        field.waitUntilShown()
        driver.type(term)
    }
}

/** A game's page: its picture and description, Play, and Game settings. */
class GamePage(private val driver: Driver) {
    val page = driver.page("Game")

    /** Starts the game and waits until it runs. */
    fun play() {
        page.find(By.id("play")).tap()
        driver.waitForGame("Running")
    }

    fun openSettings(): Element {
        page.find(By.id("settings")).tap()
        return driver.page("Game settings").waitUntilShown()
    }
}
