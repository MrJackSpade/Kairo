package com.mrjackspade.kairo.uitest.suites

import com.mrjackspade.kairo.uitest.By
import com.mrjackspade.kairo.uitest.KairoUiTest
import org.junit.Test

/**
 * Launches from a frontend, in every app: a VIEW intent for a game file starts it, before or after
 * setup, and leaving the game returns to the frontend by closing the app.
 */
abstract class LaunchTests : KairoUiTest() {
    @Test
    fun aFrontendLaunchStartsTheGame() {
        screens.completeFirstRun().waitForGame(app.bootGame)
        driver.launchExternally(screens.documents.file(app.bootGameFile))
        driver.waitForGame("Running")
        expect.equal("the game the frontend started", app.runningTitle, driver.probe().title)
    }

    /**
     * Before setup, a frontend launch starts the game when the app needs no BIOS files of the
     * player's; an app that does says which firmware to import instead of starting nothing silently.
     */
    @Test
    fun aFrontendLaunchWorksBeforeSetup() {
        driver.launchExternally(screens.documents.file(app.bootGameFile))
        // A device with a controller is asked for its layout first; the game starts after it.
        driver.waitUntil("the controller layout or the game") {
            screens.controllerLayout.exists() || driver.probeNow().state == "Running"
        }
        if (screens.controllerLayout.exists()) screens.answerControllerLayout()
        if (app.biosFiles.any { it.required }) {
            driver.waitUntil("the app to ask for the firmware the game needs") {
                driver.visibleTexts().any { it.startsWith("Launch failed: Import the") && "firmware" in it }
            }
            return
        }
        driver.waitForGame("Running")
        driver.waitFrames(SETTLE_FRAMES)
        val picture = driver.pictures.game("before-setup")
        expect.that("the game draws without the app having been set up", picture.litFraction() > 0.02) { "lit ${picture.litFraction()}" }
    }

    @Test
    fun returningFromAFrontendLaunchClosesTheApp() {
        screens.completeFirstRun().waitForGame(app.bootGame)
        driver.launchExternally(screens.documents.file(app.bootGameFile))
        driver.waitForGame("Running")
        val menu = screens.openGameMenu()
        expect.equal("the return row names the frontend", "Return to frontend",
            menu.find(By.item("f-library")).find(By.id("label")).text())
        menu.find(By.item("f-library")).tap()
        screens.confirm()
        driver.waitUntil("the app to close") { !driver.onMain { driver.anyActivityOpen() } }
    }

    @Test
    fun aSecondLaunchReplacesTheFirst() {
        screens.completeFirstRun().waitForGame(app.bootGame)
        driver.launchExternally(screens.documents.file(app.bootGameFile))
        driver.waitForGame("Running")
        driver.waitFrames(SETTLE_FRAMES)
        driver.launchExternally(screens.documents.file(app.bootGameFile))
        driver.waitForGame("Running")
        expect.equal("the game after a second launch", app.runningTitle, driver.probe().title)
    }
}
