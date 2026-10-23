package com.mrjackspade.kairo.uitest.suites

import com.mrjackspade.kairo.uitest.By
import com.mrjackspade.kairo.uitest.KairoUiTest
import org.junit.Test

/** Playing a game, in every app: it starts and draws, saves and loads its state, and exits. */
abstract class GameTests : KairoUiTest() {
    /** Frames to let a game run before judging its picture, about two seconds. */
    protected open val settleFrames = 120L

    @Test
    fun theGameStartsAndDraws() {
        screens.completeFirstRun().open(app.bootGame).play()
        driver.waitFrames(settleFrames)
        val picture = driver.pictures.game("running")
        expect.that("the game draws a picture, not a blank screen", picture.litFraction() > 0.02) { "lit ${picture.litFraction()}" }
        expect.that("the picture has more than one colour", picture.distinctColors() > 1) { "${picture.distinctColors()} colours" }
    }

    @Test
    fun exitReturnsToTheLibrary() {
        screens.completeFirstRun().open(app.bootGame).play()
        driver.waitFrames(settleFrames)
        screens.exitGame()
        expect.equal("the session after exiting", "None", driver.probe().state)
    }

    @Test
    fun aSavedStateLoadsBack() {
        screens.completeFirstRun().open(app.bootGame).play()
        driver.waitFrames(settleFrames)
        screens.openGameMenu().find(By.id("f-save")).tap()
        val save = driver.page("Save state").waitUntilShown()
        save.find(By.id("slot0")).tap()
        driver.waitUntil("the state to be saved") { !save.exists() || save.find(By.id("slot0")).texts().none { it == "Empty" } }
        driver.waitForGame("Running")
        driver.waitFrames(settleFrames)
        screens.openGameMenu().find(By.id("f-load")).tap()
        driver.page("Load state").waitUntilShown().find(By.id("slot0")).tap()
        driver.waitForGame("Running")
        driver.waitFrames(10)
        val probe = driver.probe()
        expect.equal("the game's error after loading", "", probe.details["error"] ?: "")
    }
}

/** Plain words, in every app: no screen calls games or BIOS files "ROMs". */
abstract class WordingTests : KairoUiTest() {
    // The word, not a file name's extension such as FMT_SYS.ROM.
    private val rom = Regex("(?<![.\\w])ROMs?\\b")

    private fun checkScreen(where: String) {
        driver.idle()
        val found = driver.visibleTexts().filter(rom::containsMatchIn)
        expect.that("$where never says \"ROM\"", found.isEmpty()) { found.joinToString(" | ") }
    }

    @Test
    fun noScreenSaysRom() {
        val library = screens.completeFirstRun(onPage = { title -> checkScreen("the \"$title\" step") })
            .waitForGame(app.bootGame)
        checkScreen("the library")
        library.openMenu()
        checkScreen("the library menu")
        driver.page("flyout").find(By.id("f-machine")).let { machine ->
            if (machine.exists()) {
                machine.tap()
                driver.page("Machine").waitUntilShown()
                checkScreen("the Machine page")
                driver.page("Machine").find(By.id("page.back")).tap()
            }
        }
        driver.page("flyout").find(By.id("f-manage")).tap()
        driver.page("Manage library").waitUntilShown()
        checkScreen("Manage library")
    }
}
