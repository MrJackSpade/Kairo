package com.mrjackspade.kairo.uitest.suites

import android.view.KeyEvent
import com.mrjackspade.kairo.uitest.By
import com.mrjackspade.kairo.uitest.KairoUiTest
import com.mrjackspade.kairo.uitest.MediaDrive
import com.mrjackspade.kairo.uitest.Picture
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Saved states, in every app with them: a state loads back to the moment it was saved, each slot
 * keeps its own, a used slot shows it, and states outlive the app. (KairoDos has no states: its
 * subclass leaves this suite out.)
 */
abstract class StateTests : KairoUiTest() {
    private fun play() {
        screens.completeFirstRun().open(app.bootGame).play()
        driver.waitFrames(SETTLE_FRAMES)
    }

    /**
     * Saves to [slot] from the game menu; returns the picture as it was saved (the menu pauses the
     * game). With [still], first waits for the game's picture to hold still, so that the picture a
     * load shows, a few frames on from the state, is still the saved one.
     */
    private fun save(slot: Int, still: Boolean = false): Picture {
        if (still) driver.pictures.awaitStillGame(SETTLE_FRAMES)
        val menu = screens.openGameMenu()
        driver.waitForGame("Paused")
        val picture = driver.pictures.game("saved-slot$slot")
        screens.saveState(slot, menu)
        return picture
    }

    /**
     * Loads [slot] from the game menu; returns the picture as soon as the window shows the load's. The
     * game runs on from the loaded state at once, so a game that animates moves on from it.
     */
    private fun load(slot: Int): Picture {
        screens.loadState(slot)
        // The load's picture is handed to the presenter before the app says it loaded; the window
        // shows it once the presenter has drawn it, which a slow emulator does well after that
        // (KairoMSX#63). Every picture published by now has to be drawn.
        val published = driver.probeNow().details.getValue("published").toLong()
        driver.waitUntil("the loaded picture to be drawn") {
            driver.probeNow().details["presented"]?.toLongOrNull()?.let { it >= published } == true
        }
        val picture = driver.pictures.game("loaded-slot$slot")
        driver.waitForGame("Running")
        return picture
    }

    @Test
    fun aSavedStateLoadsBackToWhereItWasSaved() {
        play()
        val saved = save(1, still = true)
        driver.waitFrames(SETTLE_FRAMES * 3)
        val loaded = load(1)
        expect.equal("the game's error after loading", "", driver.probe().details["error"].orEmpty())
        expect.that("the loaded picture is the saved one", loaded.difference(saved) < 0.1) { "difference ${loaded.difference(saved)}" }
    }

    @Test
    fun eachSlotKeepsItsOwnState() {
        play()
        val first = save(1, still = true)
        driver.waitFrames(SETTLE_FRAMES * 3)
        val second = save(2)
        val loaded = load(1)
        expect.that("slot 1 loads its own picture, not slot 2's", loaded.difference(first) <= loaded.difference(second)) {
            "difference from slot 1 ${loaded.difference(first)}, from slot 2 ${loaded.difference(second)}"
        }
    }

    @Test
    fun aStateOutlivesTheApp() {
        play()
        val saved = save(1, still = true)
        session.relaunch()
        screens.library.waitUntilShown().open(app.bootGame).play()
        val loaded = load(1)
        expect.that("the state saved before the app closed loads", loaded.difference(saved) < 0.1) { "difference ${loaded.difference(saved)}" }
    }

    /** Starts the app's multi-disk game and returns the drive its disk swap uses. */
    private fun playSwapGame(): MediaDrive {
        val swap = app.mediaSwap
        assumeTrue("${app.name} swaps no disks", swap != null)
        screens.completeFirstRun().open(swap!!.game).play()
        driver.waitFrames(SETTLE_FRAMES)
        return MediaDrive(driver, screens, swap.slot, swap.page)
    }

    /**
     * Kairo98#103, KairoTowns#76, Kairo68#69, Kairo64#49 (Kairo#103): a state saved with another
     * disk in the drive loads with that disk, and the Disks page shows it.
     */
    @Test
    fun aStateLoadsWithTheDiskItWasSavedWith() {
        val drive = playSwapGame()
        val insert = app.mediaSwap!!.insert
        val started = drive.holds()
        drive.pick(drive.openChoice(), MediaDrive.diskWith(insert))
        drive.awaitHolds(insert) { it.contains(insert) }
        screens.closeMenu()
        driver.waitForGame("Running")
        save(1)
        val back = drive.openChoice()
        if (started.isEmpty()) drive.eject(back) else drive.pick(back, MediaDrive.disk(started))
        drive.awaitHolds("its first disk again") { it == started }
        screens.closeMenu()
        driver.waitForGame("Running")
        load(1)
        drive.awaitHolds("the state's disk, $insert") { it.contains(insert) }
        screens.openGameMenu().find(By.item(app.mediaSwap!!.page)).tap()
        val row = driver.find(By.anyItem().withText(drive.slot)).texts()
        expect.that("the Disks page shows the state's disk in ${drive.slot}", row.any { it.contains(insert) }) { row.joinToString(" | ") }
    }

    /** Kairo68#73 (Kairo#103): a save right after an eject, the menu still open, succeeds. */
    @Test
    fun aStateSavesRightAfterAnEject() {
        val drive = playSwapGame()
        drive.eject(drive.openChoice())
        drive.awaitHolds("nothing") { it.isEmpty() }
        // From the media page straight back to the menu, where the game is still held, and save.
        driver.press(KeyEvent.KEYCODE_BUTTON_B)
        screens.saveState(1, driver.page("flyout").waitUntilShown())
    }

    /** KairoTowns#76 (Kairo#103): a state saved with a CD loads with it after the CD was ejected. */
    @Test
    fun aStateLoadsWithTheCdItWasSavedWith() {
        val cd = app.cdDrive
        assumeTrue("${app.name}'s machine has no CD drive", cd != null)
        screens.completeFirstRun().open(cd!!.game).play()
        driver.waitFrames(SETTLE_FRAMES)
        val drive = MediaDrive(driver, screens, cd.slot, cd.page)
        val disc = drive.holds()
        expect.that("${cd.game} starts with a disc in ${cd.slot}", disc.isNotEmpty())
        save(1)
        drive.eject(drive.openChoice())
        drive.awaitHolds("nothing") { it.isEmpty() }
        screens.closeMenu()
        driver.waitForGame("Running")
        load(1)
        drive.awaitHolds("the state's disc, $disc") { it == disc }
    }

    @Test
    fun aGamesStatesAreItsOwn() {
        play()
        save(1)
        screens.endGame().open(app.otherGame).play()
        screens.openGameMenu().find(By.item("f-load")).tap()
        val slot = driver.page("Load state").waitUntilShown().find(By.item("slot1"))
        val texts = slot.texts()
        expect.that("${app.otherGame}'s first slot is empty", texts.any { it.contains("Empty", ignoreCase = true) }) { texts.joinToString(" | ") }
    }
}
