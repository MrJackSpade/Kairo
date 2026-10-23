package com.mrjackspade.kairo.uitest.suites

import com.mrjackspade.kairo.uitest.KairoUiTest
import com.mrjackspade.kairo.uitest.MenuCrawler
import org.junit.Test

/**
 * Every menu, in every app: a crawl opens every page and item reachable from the library menu, the
 * game menu, and a game's settings (MenuCrawler), checking each page and that the crawl can always
 * find its way back. New rows are covered as soon as they exist.
 */
abstract class MenuTests : KairoUiTest() {
    private fun crawl(where: String, minimumPages: Int, minimumItems: Int) {
        val report = MenuCrawler(driver, checkPage = { page -> Checks.page(driver, expect, page) }).crawl()
        expect.that("the $where crawl reached at least $minimumPages pages", report.pages.size >= minimumPages) { report.toString() }
        expect.that("the $where crawl opened at least $minimumItems items", report.items.size >= minimumItems) { report.toString() }
    }

    @Test
    fun everyPageOfTheLibraryMenuOpens() {
        screens.completeFirstRun().waitForGame(app.bootGame).openMenu()
        crawl("library menu", minimumPages = 8, minimumItems = 30)
    }

    @Test
    fun everyPageOfTheGameMenuOpens() {
        screens.completeFirstRun().open(app.bootGame).play()
        driver.waitFrames(SETTLE_FRAMES)
        screens.openGameMenu()
        crawl("game menu", minimumPages = 8, minimumItems = 30)
    }

    @Test
    fun everyPageOfAGamesSettingsOpens() {
        screens.completeFirstRun().open(app.bootGame).openSettings()
        crawl("game settings", minimumPages = 3, minimumItems = 8)
    }
}
