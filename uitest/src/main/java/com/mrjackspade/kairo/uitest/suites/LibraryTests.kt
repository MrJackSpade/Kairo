package com.mrjackspade.kairo.uitest.suites

import com.mrjackspade.kairo.uitest.By
import com.mrjackspade.kairo.uitest.KairoUiTest
import com.mrjackspade.kairo.uitest.Library
import org.junit.Test

/** The game library, in every app: its header, one entry per game, and no non-games. */
abstract class LibraryTests : KairoUiTest() {
    @Test
    fun theHeaderShowsTheProductName() {
        val library = screens.completeFirstRun()
        expect.equal("the library header", app.brand, library.brand.text())
    }

    @Test
    fun eachGameIsOneEntry() {
        val library = screens.completeFirstRun().waitForGame(app.bootGame)
        app.singleEntryGames.forEach(library::waitForGame)
        val titles = library.entries().map { it.title }
        for (game in app.singleEntryGames) expect.equal("entries titled \"$game\"", 1, titles.count { it == game })
    }

    /**
     * Kairo#39: the scan reads each archive where it is. After it, no file in the app's own
     * storage, apart from the test games folder, is a copy of one of the archives it read.
     */
    @Test
    fun theScanReadsArchivesWhereTheyAre() {
        screens.completeFirstRun().waitForGame(app.bootGame)
        val target = driver.instrumentation.targetContext
        val games = java.io.File(target.filesDir, com.mrjackspade.kairo.frontend.UiTestDocuments.DIRECTORY)
        fun digest(file: java.io.File) = java.security.MessageDigest.getInstance("SHA-256").let { sha ->
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) { val read = input.read(buffer); if (read < 0) break; sha.update(buffer, 0, read) }
            }
            sha.digest().joinToString("") { "%02x".format(it) }
        }
        val archives = games.walkTopDown().filter { it.isFile && it.extension.lowercase() in setOf("zip", "7z", "lzh") }
            .associateBy({ it.length() to digest(it) }, { it.name })
        org.junit.Assume.assumeTrue("${app.name}'s test games are not in archives", archives.isNotEmpty())
        val sizes = archives.keys.map { it.first }.toSet()
        val copies = sequenceOf(target.filesDir, target.cacheDir).flatMap { root ->
            root.walkTopDown().onEnter { it != games }.filter { it.isFile && it.length() in sizes }
        }.mapNotNull { file -> archives[file.length() to digest(file)]?.let { "${file.path} copies $it" } }.toList()
        driver.log.step("storage", "${archives.size} archives scanned; ${copies.size} copies in app storage")
        expect.that("no archive is copied into app storage", copies.isEmpty()) { copies.joinToString() }
    }

    @Test
    fun differentReleasesStaySeparate() {
        val library = screens.completeFirstRun().waitForGame(app.bootGame)
        app.separateGames.forEach(library::waitForGame)
        val titles = library.entries().map { it.title }
        for (game in app.separateGames) expect.equal("entries titled \"$game\"", 1, titles.count { it == game })
    }

    @Test
    fun nonGamesAreNeverListed() {
        val library = screens.completeFirstRun().waitForGame(app.bootGame)
        val entries = library.entries()
        for (file in app.hiddenFiles) {
            val listed = entries.filter { it.file.contains(file, ignoreCase = true) }
            expect.that("nothing from \"$file\" is listed", listed.isEmpty()) { listed.joinToString { it.file } }
        }
    }

    @Test
    fun theLowerScreenGivesNoInstructions() {
        val library = screens.completeFirstRun()
        library.entry(app.bootGame).tap()
        driver.idle()
        val texts = driver.visibleTexts()
        val instruction = texts.filter { it.contains("tap the game above", ignoreCase = true) }
        expect.that("no screen tells the player how to open a game", instruction.isEmpty()) { instruction.joinToString() }
    }
}

/** Library search, in every app, on one screen or two. */
abstract class SearchTests : KairoUiTest() {
    @Test
    fun searchFindsTheGameAndOnlyMatchingGames() {
        val library = screens.completeFirstRun().waitForGame(app.bootGame)
        library.search(app.searchTerm)
        library.waitForGame(app.bootGame)
        // The list filters as the term is typed, so it may still show earlier results at first.
        fun others(entries: List<Library.Entry>) =
            entries.filterNot { (it.title + " " + it.file).contains(app.searchTerm, ignoreCase = true) }
        var entries = emptyList<Library.Entry>()
        driver.waitUntil("every result to match \"${app.searchTerm}\"") {
            entries = library.entries()
            others(entries).isEmpty()
        }
        expect.that("\"${app.bootGame}\" is a result", entries.any { it.title == app.bootGame })
    }

    /** Kairo#29: the search field lets the system show its full-screen keyboard, as every text field does. */
    @Test
    fun theSearchFieldAllowsTheFullScreenKeyboard() {
        val library = screens.completeFirstRun().waitForGame(app.bootGame)
        library.search(app.searchTerm)
        val fields = driver.onMain {
            driver.roots().flatMap { com.mrjackspade.kairo.uitest.Views.descendants(it).toList() }
                .filterIsInstance<android.widget.EditText>().filter { it.isShown }
                .map { (com.mrjackspade.kairo.frontend.TestAttributes.id(it) ?: "field") to it.imeOptions }
        }
        expect.that("a text field is shown", fields.isNotEmpty())
        for ((id, options) in fields)
            expect.that("$id allows the full-screen keyboard", options and android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI == 0)
    }

    @Test
    fun closingSearchBringsBackEveryGame() {
        val library = screens.completeFirstRun().waitForGame(app.bootGame)
        val all = library.entries().size
        library.search(app.searchTerm)
        library.waitForGame(app.bootGame)
        expect.that("the search narrows the library", library.entries().size < all || all == 1) { "${library.entries().size} of $all" }
        // The search button closes search and clears its text.
        driver.find(By.id("library.search")).tap()
        driver.waitUntil("every game to be listed again") { library.entries().size == all }
    }

    @Test
    fun aSearchWithNoMatchShowsNoGames() {
        val library = screens.completeFirstRun().waitForGame(app.bootGame)
        library.search("zzqqxx")
        driver.waitUntil("no game to be listed") { !driver.find(By.id("library.entry")).exists() }
    }

    @Test
    fun onTwoScreensSearchIsTypedOnTheLowerScreen() {
        val library = screens.completeFirstRun()
        org.junit.Assume.assumeTrue("this device has one screen", driver.find(By.id("secondary.library")).exists())
        driver.find(By.id("library.search")).tap()
        val lower = driver.find(By.id("secondary.search.field")).waitUntilShown()
        driver.waitUntil("the lower screen's search field to take focus") {
            driver.onMain { driver.resolve(listOf(By.id("secondary.search.field"))).lastOrNull()?.hasFocus() == true }
        }
        driver.type(app.searchTerm)
        expect.equal("the lower screen's search text", app.searchTerm, lower.text())
        library.waitForGame(app.bootGame)
        expect.that("the library on the upper screen shows the results", library.entry(app.bootGame).exists())
    }

    /**
     * Kairo#43: on two screens the lower one shows the selected game's page while the player
     * browses; once the game is open above, its settings are below and the page above has no
     * Game settings button; Back on those settings closes the game above.
     */
    @Test
    fun onTwoScreensTheLowerScreenShowsTheGameThenItsSettings() {
        val library = screens.completeFirstRun().waitForGame(app.bootGame)
        val lower = driver.find(By.id("secondary.library"))
        org.junit.Assume.assumeTrue("this device has one screen", lower.exists())
        library.entry(app.bootGame)
        driver.waitUntil("the lower screen to show a game page") { lower.find(By.id("play")).exists() }
        lower.find(By.id("settings")).tap()
        val upper = driver.page("Game").waitUntilShown()
        val settings = driver.page("Game settings").waitUntilShown()
        expect.that("the upper game page has no Game settings button", !upper.find(By.id("settings")).exists())
        settings.find(By.id("page.back")).tap()
        upper.waitUntilGone()
        driver.waitUntil("the lower screen to show the game page again") { lower.find(By.id("play")).exists() }
    }
}
