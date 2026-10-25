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
     * Picks the [disk] option from the drive's [choices]: among the game's own disks, or else on
     * Other's page, scrolled to; returns once the choice has closed.
     */
    private fun pick(choices: Element, disk: By) {
        val own = choices.find(disk)
        if (own.exists()) own.tap()
        else {
            choices.find(By.item("f:other")).tap()
            val other = driver.page("Other").waitUntilShown()
            other.find(disk).revealIn(rows(other)).tap()
        }
        driver.waitUntil("the disk choice to close") { !driver.page(swap.slot).exists() && !driver.page("Other").exists() }
    }

    /** The rows of a choice [page]: a recycled list on a long page, a scroll view on a short one. */
    private fun rows(page: Element): Element {
        val list = page.find(By.id("page.list"))
        val scroll = page.find(By.id("page.scroll"))
        driver.waitUntil("the rows of ${page.description}") { list.exists() || scroll.exists() }
        return if (list.exists()) list else scroll
    }

    /**
     * Disk options of a choice page, by their ids ("o:<entry id>", the path below the games folder
     * and inside an archive): a row's label names an archive of one disk by the archive alone.
     */
    private fun diskWith(part: String) = By.idMatching(Regex("o:.*${Regex.escape(part)}.*"))

    /** The option of the disk image the machine reports as [file]. */
    private fun disk(file: String) = By.idMatching(Regex("o:(.*/)?${Regex.escape(file)}"))

    private fun drive(): String = driver.probeNow().details["media.${swap.slot}"].orEmpty()

    /** What every drive holds now, empty drives left out. */
    private fun media(): List<String> =
        driver.probeNow().details.filterKeys { it.startsWith("media.") }.values.filter { it.isNotEmpty() }

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
        pick(startAndOpenChoice(), diskWith(swap.insert))
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
        // The first game's own disks, for a second game that already has the usual disk in a drive.
        val firstDisks = media().filterNot { it.contains(swap.insert) }
        pick(choices, diskWith(swap.insert))
        awaitDrive(swap.insert) { it.contains(swap.insert) }
        screens.closeMenu()
        expectRunning()
        // Back to what the drive started with, then the other disk again.
        val back = openChoice()
        if (started.isEmpty()) {
            back.find(By.item("o:empty")).tap()
            back.waitUntilGone()
        } else pick(back, disk(started))
        awaitDrive("its first disk again") { it == started }
        screens.closeMenu()
        expectRunning()
        pick(openChoice(), diskWith(swap.insert))
        awaitDrive(swap.insert) { it.contains(swap.insert) }
        screens.closeMenu()
        expectRunning()
        // A second game in the same session of the app.
        screens.endGame()
        val second = if (app.bootGame != swap.game) app.bootGame else app.otherGame
        val secondChoices = startAndOpenChoice(second, fromLibrary = true)
        // A disk the second game is not using: one image goes in one drive at a time.
        if (media().any { it.contains(swap.insert) }) {
            val insert = firstDisks.first()
            pick(secondChoices, disk(insert))
            awaitDrive(insert) { it == insert }
        } else {
            pick(secondChoices, diskWith(swap.insert))
            awaitDrive(swap.insert) { it.contains(swap.insert) }
        }
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
        other.find(diskWith(swap.insert)).revealIn(rows(other)).tap()
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
