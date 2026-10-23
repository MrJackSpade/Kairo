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

    /** Closes the menu and any page over the game, as the menu button does, when one is open. */
    fun closeMenu() {
        driver.idle()
        val pages = driver.find(By.id("page"))
        if (pages.exists()) {
            driver.press(KeyEvent.KEYCODE_BUTTON_MODE)
            pages.waitUntilGone()
        }
    }

    /**
     * Opens the choice row [row] on [page] and picks [option], or else the first option not chosen
     * now; returns the option's id once the choice page has closed.
     */
    fun choose(page: Element, row: String, option: String? = null): String {
        val title = page.find(By.item(row)).find(By.id("label")).text()
        page.find(By.item(row)).tap()
        val choices = driver.page(title).waitUntilShown()
        val picked = option?.let { choices.find(By.item("o:$it")) }
            ?: choices.find(By.anyItem()).all().first { it.key() == "false" && it.itemId().startsWith("o:") }
        val id = picked.itemId().removePrefix("o:")
        picked.tap()
        choices.waitUntilGone()
        driver.log.step("choose", "$row = $id")
        return id
    }

    /**
     * Maps [control] ("a", "b", "start", …) to the first key of the guest's keyboard on the Button
     * mapping page [mapping], saves, and returns the key's guest code.
     */
    fun mapToFirstKey(mapping: Element, control: String): Int {
        mapping.find(By.item("m:$control")).tap()
        val target = driver.page("Button target").waitUntilShown()
        target.find(By.item("tab:KEYS")).tap()
        val key = target.find(By.id("target.key")).nth(0)
        val code = checkNotNull(key.key()?.toInt()) { "The key picker's first key has no code" }
        key.tap()
        target.find(By.id("footer:save")).tap()
        target.waitUntilGone()
        driver.log.step("map", "$control = key $code")
        return code
    }

    /** The running game's settings, from its menu. */
    fun openGameSettingsInGame(): Element {
        openGameMenu().find(By.item("f-game")).tap()
        return driver.page("Game settings").waitUntilShown()
    }

    /**
     * Confirms what the last tap asked about, by its button that is not Cancel: a modal's (titled
     * confirmations) or the tray's under the row (the others).
     */
    fun confirm() {
        val modal = driver.find(By.id("modal.button"))
        val tray = driver.find(By.id("tray.button"))
        driver.waitUntil("a confirmation") { modal.exists() || tray.exists() }
        val buttons = if (modal.exists()) modal.all() else tray.all()
        buttons.first { it.key() != "Cancel" }.tap()
    }

    /** Ends the running game and returns to the library: the menu's Return to library, confirmed. */
    fun endGame(): Library {
        openGameMenu().find(By.item("f-library")).tap()
        confirm()
        return library.waitUntilShown()
    }

    /** Closes the app through the menu's Exit, confirmed, and waits until no activity of it is open. */
    fun exitApp(menu: Element) {
        menu.find(By.item("f-exit")).tap()
        confirm()
        driver.waitUntil("the app to close") { !driver.onMain { driver.anyActivityOpen() } }
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
