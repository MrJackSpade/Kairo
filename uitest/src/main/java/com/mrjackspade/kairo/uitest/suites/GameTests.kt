package com.mrjackspade.kairo.uitest.suites

import com.mrjackspade.kairo.uitest.By
import com.mrjackspade.kairo.uitest.KairoUiTest
import org.junit.Test

/** Frames to let a game run before judging its picture, about two seconds. */
internal const val SETTLE_FRAMES = 120L

/** Playing a game, in every app: it starts and draws, and exits. */
abstract class GameTests : KairoUiTest() {
    @Test
    fun theGameStartsAndDraws() {
        screens.completeFirstRun().open(app.bootGame).play()
        driver.waitFrames(SETTLE_FRAMES)
        val picture = driver.pictures.game("running")
        expect.that("the game draws a picture, not a blank screen", picture.litFraction() > 0.02) { "lit ${picture.litFraction()}" }
        expect.that("the picture has more than one colour", picture.distinctColors() > 1) { "${picture.distinctColors()} colours" }
        expect.equal("the running game's title", app.runningTitle, driver.probe().title)
    }

    @Test
    fun returnToLibraryEndsTheGame() {
        screens.completeFirstRun().open(app.bootGame).play()
        driver.waitFrames(SETTLE_FRAMES)
        screens.endGame()
        expect.equal("the session after returning to the library", "None", driver.probe().state)
    }

    @Test
    fun exitClosesTheApp() {
        screens.completeFirstRun().open(app.bootGame).play()
        driver.waitFrames(SETTLE_FRAMES)
        screens.exitApp(screens.openGameMenu())
    }

    @Test
    fun restartBootsTheGameAgain() {
        screens.completeFirstRun().open(app.bootGame).play()
        driver.waitFrames(SETTLE_FRAMES)
        val before = driver.probe().details["frames"]!!.toLong()
        screens.openGameMenu().find(By.item("f-restart")).tap()
        screens.confirm()
        // A restart boots the machine again, so its frame count starts over.
        driver.waitUntil("the frame count to start over") { driver.probeNow().details["frames"]?.toLongOrNull()?.let { it < before } == true }
        driver.waitForGame("Running")
        expect.equal("the restarted game's title", app.runningTitle, driver.probe().title)
    }

    @Test
    fun theMenuPausesTheGame() {
        screens.completeFirstRun().open(app.bootGame).play()
        driver.waitFrames(SETTLE_FRAMES)
        screens.openGameMenu()
        driver.waitForGame("Paused")
        val frames = driver.probe().details["frames"]
        Thread.sleep(500) // Half a second in which a running game would draw about 30 frames.
        expect.equal("frames while the menu is open", frames, driver.probe().details["frames"])
        screens.closeMenu()
        driver.waitForGame("Running")
    }
}

/** Plain words, in every app: no screen calls games or BIOS files "ROMs". */
abstract class WordingTests : KairoUiTest() {
    @Test
    fun noScreenSaysRom() {
        val library = screens.completeFirstRun(onPage = { title -> Checks.noRom(driver, expect, "the \"$title\" step") })
            .waitForGame(app.bootGame)
        Checks.noRom(driver, expect, "the library")
        library.open(app.bootGame)
        Checks.noRom(driver, expect, "the game page")
    }
}
