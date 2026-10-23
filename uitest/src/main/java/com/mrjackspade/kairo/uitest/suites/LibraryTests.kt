package com.mrjackspade.kairo.uitest.suites

import com.mrjackspade.kairo.uitest.By
import com.mrjackspade.kairo.uitest.KairoUiTest
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
        val entries = library.entries()
        val others = entries.filterNot { (it.title + " " + it.file).contains(app.searchTerm, ignoreCase = true) }
        expect.that("every result matches \"${app.searchTerm}\"", others.isEmpty()) { others.joinToString { it.title } }
        expect.that("\"${app.bootGame}\" is a result", entries.any { it.title == app.bootGame })
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
}
