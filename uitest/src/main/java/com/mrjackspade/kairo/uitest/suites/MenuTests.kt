package com.mrjackspade.kairo.uitest.suites

import android.view.KeyEvent
import com.mrjackspade.kairo.uitest.By
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

    /** Kairo#28: a cycling choice shows its ‹ whether or not it is selected, and its value never moves. */
    @Test
    fun aCyclingChoiceShowsItsArrowAndKeepsItsValueInPlace() {
        screens.completeFirstRun().openMenu().find(By.item("f-controls")).tap()
        val controls = driver.page("Controls").waitUntilShown()
        val row = controls.find(By.item("osc"))
        val value = row.find(By.id("value"))
        expect.that("the ‹ shows on a row that is not selected", row.find(By.id("cycle")).exists())
        val before = value.boundsInWindowNow()
        // Down from nothing selected selects the first row; on until the row is the selected one.
        val focused = controls.find(By.focusedItem())
        for (step in 0 until controls.find(By.anyItem()).count()) {
            if (focused.exists() && focused.itemId() == "osc") break
            driver.press(KeyEvent.KEYCODE_DPAD_DOWN)
        }
        expect.equal("the selected row", "osc", focused.itemId())
        expect.that("the ‹ shows on the selected row", row.find(By.id("cycle")).exists())
        expect.equal("the value's place once the row is selected", before, value.boundsInWindowNow())
    }

    /** Kairo#30: Down and Up move the selection through a long page and keep it on screen both ways. */
    @Test
    fun theDPadKeepsTheSelectionOnScreen() {
        screens.completeFirstRun().openMenu().find(By.item("f-controls")).tap()
        driver.page("Controls").waitUntilShown().find(By.item("mapping")).tap()
        val page = driver.page("Button mapping").waitUntilShown()
        val focused = page.find(By.focusedItem())
        val rows = page.find(By.anyItem()).count()
        fun onScreenAfter(step: String) =
            driver.waitUntil("the selected row to be fully on screen after $step") { focused.fullyOnScreenNow() }
        repeat(rows) { driver.press(KeyEvent.KEYCODE_DPAD_DOWN); onScreenAfter("Down ${it + 1}") }
        val bottom = focused.itemId()
        repeat(rows) { driver.press(KeyEvent.KEYCODE_DPAD_UP); onScreenAfter("Up ${it + 1}") }
        expect.that("Up went back from $bottom", focused.itemId() != bottom)
    }

    /** Kairo#33: Download missing images answers the press on its row before anything downloads. */
    @Test
    fun downloadingImagesShowsItIsWorking() {
        screens.completeFirstRun().openMenu().find(By.item("f-manage")).tap()
        val manage = driver.page("Manage library").waitUntilShown()
        val images = manage.find(By.item("images"))
        org.junit.Assume.assumeTrue("${app.name} downloads no artwork", images.exists())
        val value = images.find(By.id("value"))
        val before = value.textNow()
        images.tap()
        driver.waitUntil("the row to show it is working") { value.textNow().let { it != null && it.isNotEmpty() && it != before } }
        driver.log.step("images", "the row shows \"${value.textNow()}\"")
        driver.find(By.id("tray.button").withText("Stop")).takeIf { it.exists() }?.tap()
    }
}
