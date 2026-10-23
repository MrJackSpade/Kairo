package com.mrjackspade.kairo.uitest.suites

import com.mrjackspade.kairo.uitest.By
import com.mrjackspade.kairo.uitest.KairoUiTest
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Changing media while a game runs, in every app with a media page: inserting another disk of the
 * game's folder, and ejecting one, show in the drive the machine reports.
 */
abstract class MediaTests : KairoUiTest() {
    private fun openMediaRow(): com.mrjackspade.kairo.uitest.Element {
        val swap = app.mediaSwap!!
        screens.completeFirstRun().open(swap.game).play()
        driver.waitFrames(SETTLE_FRAMES)
        screens.openGameMenu().find(By.item("f-media")).tap()
        // The media page's rows are "media<slot>", labelled by the slot; each opens a choice page titled by it.
        driver.find(By.idMatching(Regex("media\\d+")).withText(swap.slot)).tap()
        return driver.page(swap.slot).waitUntilShown()
    }

    @Test
    fun insertingADiskPutsItInTheDrive() {
        assumeTrue("${app.name} has no media page", app.mediaSwap != null)
        val swap = app.mediaSwap!!
        val choices = openMediaRow()
        choices.find(By.anyItem().withTextContaining(swap.insert)).tap()
        choices.waitUntilGone()
        driver.waitUntil("${swap.slot} to hold the inserted disk") {
            driver.probeNow().details["media.${swap.slot}"].orEmpty().contains(swap.insert)
        }
        screens.closeMenu()
        driver.waitForGame("Running")
    }

    @Test
    fun ejectingADiskEmptiesTheDrive() {
        assumeTrue("${app.name} has no media page", app.mediaSwap != null)
        val swap = app.mediaSwap!!
        val choices = openMediaRow()
        choices.find(By.item("o:empty")).tap()
        choices.waitUntilGone()
        driver.waitUntil("${swap.slot} to be empty") { driver.probeNow().details["media.${swap.slot}"].orEmpty().isEmpty() }
        screens.closeMenu()
        driver.waitForGame("Running")
    }
}
