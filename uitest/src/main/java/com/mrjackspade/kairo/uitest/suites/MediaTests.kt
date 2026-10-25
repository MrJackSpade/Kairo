package com.mrjackspade.kairo.uitest.suites

import com.mrjackspade.kairo.uitest.AppUnderTest
import com.mrjackspade.kairo.uitest.By
import com.mrjackspade.kairo.uitest.Element
import com.mrjackspade.kairo.uitest.KairoUiTest
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Changing media while a game runs, in every app with a media page (Kairo#47, #48): the drive's
 * choice lists the game's own disks and Other, every disk of the library, which opens promptly
 * however many there are; inserting, swapping back and swapping again show in the drive the
 * machine reports while the game runs on, in more than one game of a session. KairoDos has no
 * media page (DOSBox mounts a game's folder); its MediaTests are skipped with that reason.
 */
abstract class MediaTests : KairoUiTest() {
    private val swap: AppUnderTest.MediaSwap get() = app.mediaSwap!!

    private fun requireMediaPage() = assumeTrue("${app.name} has no media page", app.mediaSwap != null)

    /** Starts [game] and opens the drive's choice from its menu. */
    private fun startAndOpenChoice(game: String = swap.game, fromLibrary: Boolean = false): Element {
        val library = if (fromLibrary) screens.library else screens.completeFirstRun()
        library.open(game).play()
        driver.waitFrames(SETTLE_FRAMES)
        return openChoice()
    }

    /** The drive's choice page, from the game menu's media page. */
    private fun openChoice(): Element {
        screens.openGameMenu().find(By.item(swap.page)).tap()
        driver.find(By.anyItem().withText(swap.slot)).tap()
        return driver.page(swap.slot).waitUntilShown()
    }

    /**
     * Picks the disk named [name] from the drive's [choices]: among the game's own disks, or else
     * on Other's page, scrolled to; returns once the choice has closed.
     */
    private fun pick(choices: Element, name: String) {
        val own = choices.find(By.anyItem().withTextContaining(name))
        if (own.exists()) own.tap()
        else {
            choices.find(By.item("f:other")).tap()
            val other = driver.page("Other").waitUntilShown()
            other.find(By.anyItem().withTextContaining(name)).revealIn(other.find(By.id("page.list"))).tap()
        }
        driver.waitUntil("the disk choice to close") { !driver.page(swap.slot).exists() && !driver.page("Other").exists() }
    }

    private fun drive(): String = driver.probeNow().details["media.${swap.slot}"].orEmpty()

    private fun awaitDrive(what: String, holds: (String) -> Boolean) =
        driver.waitUntil("${swap.slot} to hold $what") { holds(drive()) }

    /** The game still runs: its frame count moves on. */
    private fun expectRunning() {
        val before = driver.probe().details["frames"]!!.toLong()
        driver.waitUntil("the game to keep running") { (driver.probeNow().details["frames"]?.toLongOrNull() ?: 0) > before }
    }

    @Test
    fun insertingADiskPutsItInTheDrive() {
        requireMediaPage()
        pick(startAndOpenChoice(), swap.insert)
        awaitDrive(swap.insert) { it.contains(swap.insert) }
        screens.closeMenu()
        driver.waitForGame("Running")
    }

    @Test
    fun ejectingADiskEmptiesTheDrive() {
        requireMediaPage()
        val choices = startAndOpenChoice()
        choices.find(By.item("o:empty")).tap()
        choices.waitUntilGone()
        awaitDrive("nothing") { it.isEmpty() }
        screens.closeMenu()
        driver.waitForGame("Running")
    }

    @Test
    fun swappingBackAndAgainKeepsTheGameRunningInEveryGame() {
        requireMediaPage()
        val choices = startAndOpenChoice()
        val started = drive()
        pick(choices, swap.insert)
        awaitDrive(swap.insert) { it.contains(swap.insert) }
        screens.closeMenu()
        expectRunning()
        // Back to what the drive started with, then the other disk again.
        val back = openChoice()
        if (started.isEmpty()) {
            back.find(By.item("o:empty")).tap()
            back.waitUntilGone()
        } else pick(back, started.substringBeforeLast('.'))
        awaitDrive("its first disk again") { it == started }
        screens.closeMenu()
        expectRunning()
        pick(openChoice(), swap.insert)
        awaitDrive(swap.insert) { it.contains(swap.insert) }
        screens.closeMenu()
        expectRunning()
        // A second game in the same session of the app.
        screens.endGame()
        val second = if (app.bootGame != swap.game) app.bootGame else app.otherGame
        pick(startAndOpenChoice(second, fromLibrary = true), swap.insert)
        awaitDrive(swap.insert) { it.contains(swap.insert) }
        screens.closeMenu()
        expectRunning()
    }

    @Test
    fun theDiskChoiceOpensPromptlyWithThousandsOfDisks() {
        requireMediaPage()
        val filler = checkNotNull(swap.filler) { "${app.name}'s MediaSwap names no filler extension for its library" }
        screens.documents.addFiller("games/zz Filler", filler, FILLER_DISKS)
        screens.completeFirstRun().waitForGame(swap.game)
        screens.library.open(swap.game).play()
        driver.waitFrames(SETTLE_FRAMES)
        screens.openGameMenu().find(By.item(swap.page)).tap()
        val opened = System.nanoTime()
        driver.find(By.anyItem().withText(swap.slot)).tap()
        val choices = driver.page(swap.slot).waitUntilShown()
        val choiceMillis = (System.nanoTime() - opened) / 1_000_000
        val otherOpened = System.nanoTime()
        choices.find(By.item("f:other")).tap()
        val other = driver.page("Other").waitUntilShown()
        val otherMillis = (System.nanoTime() - otherOpened) / 1_000_000
        driver.log.step("timing", "choice $choiceMillis ms, Other $otherMillis ms with $FILLER_DISKS filler disks")
        expect.that("the drive's choice opens promptly", choiceMillis < PROMPT_MILLIS) { "$choiceMillis ms" }
        expect.that("Other opens promptly with $FILLER_DISKS more disks", otherMillis < PROMPT_MILLIS) { "$otherMillis ms" }
        other.find(By.anyItem().withTextContaining(swap.insert)).revealIn(other.find(By.id("page.list"))).tap()
        awaitDrive(swap.insert) { it.contains(swap.insert) }
        screens.closeMenu()
        expectRunning()
    }

    private companion object {
        const val FILLER_DISKS = 2000
        /** A page that opens within this feels immediate; one that builds every row took seconds and froze the app. */
        const val PROMPT_MILLIS = 1500L
    }
}
