package com.mrjackspade.kairo.uitest.suites

import com.mrjackspade.kairo.uitest.By
import com.mrjackspade.kairo.uitest.KairoUiTest
import com.mrjackspade.kairo.uitest.Picture
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

    /** Saves to [slot] from the game menu; returns the picture as it was saved (the menu pauses the game). */
    private fun save(slot: Int): Picture {
        val menu = screens.openGameMenu()
        driver.waitForGame("Paused")
        val picture = driver.pictures.game("saved-slot$slot")
        menu.find(By.item("f-save")).tap()
        val page = driver.page("Save state").waitUntilShown()
        val before = page.find(By.item("slot$slot")).texts()
        page.find(By.item("slot$slot")).tap()
        driver.waitUntil("slot $slot to show the saved state") { !page.exists() || page.find(By.item("slot$slot")).texts() != before }
        screens.closeMenu()
        driver.waitForGame("Running")
        return picture
    }

    /** Loads [slot] from the game menu; returns the picture once loaded, with the menu still pausing it. */
    private fun load(slot: Int): Picture {
        screens.openGameMenu().find(By.item("f-load")).tap()
        driver.page("Load state").waitUntilShown().find(By.item("slot$slot")).tap()
        screens.closeMenu()
        driver.waitForGame("Running")
        screens.openGameMenu()
        driver.waitForGame("Paused")
        val picture = driver.pictures.game("loaded-slot$slot")
        screens.closeMenu()
        return picture
    }

    @Test
    fun aSavedStateLoadsBackToWhereItWasSaved() {
        play()
        val saved = save(1)
        driver.waitFrames(SETTLE_FRAMES * 3)
        val loaded = load(1)
        expect.equal("the game's error after loading", "", driver.probe().details["error"].orEmpty())
        expect.that("the loaded picture is the saved one", loaded.difference(saved) < 0.1) { "difference ${loaded.difference(saved)}" }
    }

    @Test
    fun eachSlotKeepsItsOwnState() {
        play()
        val first = save(1)
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
        val saved = save(1)
        session.relaunch()
        screens.library.waitUntilShown().open(app.bootGame).play()
        val loaded = load(1)
        expect.that("the state saved before the app closed loads", loaded.difference(saved) < 0.1) { "difference ${loaded.difference(saved)}" }
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
