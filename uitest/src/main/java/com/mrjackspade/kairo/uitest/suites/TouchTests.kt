package com.mrjackspade.kairo.uitest.suites

import com.mrjackspade.kairo.uitest.By
import com.mrjackspade.kairo.uitest.KairoUiTest
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The touch screen, in every app: the on-screen controls, the touch keyboard, the touchpad, and
 * tapping the game. Touches are synthesized on the elements where they are at that moment; what
 * the game receives is read from the app's record of guest input.
 */
abstract class TouchTests : KairoUiTest() {
    private fun play() {
        screens.completeFirstRun().open(app.bootGame).play()
        driver.waitFrames(SETTLE_FRAMES)
    }

    /** Shows the on-screen controls (Controls › On-screen controls › Shown) and returns to the game. */
    private fun showOnScreenControls() {
        screens.openGameMenu().find(By.item("f-controls")).tap()
        screens.choose(driver.page("Controls").waitUntilShown(), "osc", "SHOWN")
        screens.closeMenu()
        driver.find(By.id("touch.control")).waitUntilShown()
    }

    @Test
    fun anOnScreenButtonReachesTheGame() {
        play()
        showOnScreenControls()
        for (control in listOf("a", "b", "start")) {
            val mark = driver.inputMark()
            driver.find(By.id("touch.control").withKey(control)).hold(120)
            val events = driver.inputSince(mark)
            expect.that("the on-screen $control button sends the game something", events.isNotEmpty())
            val record = driver.activity().guestInput
            expect.that("the on-screen $control button is released", record.keysHeld.isEmpty() && record.controlsHeld.isEmpty()) {
                "keys ${record.keysHeld}, controls ${record.controlsHeld}"
            }
        }
    }

    @Test
    fun theOnScreenDpadReachesTheGame() {
        play()
        showOnScreenControls()
        for (direction in listOf("up", "down", "left", "right")) {
            val mark = driver.inputMark()
            driver.find(By.id("touch.control").withKey(direction)).hold(120)
            expect.that("the on-screen $direction sends the game something", driver.inputSince(mark).isNotEmpty())
        }
    }

    @Test
    fun hidingTheOnScreenControlsRemovesThem() {
        play()
        showOnScreenControls()
        screens.openGameMenu().find(By.item("f-controls")).tap()
        screens.choose(driver.page("Controls").waitUntilShown(), "osc", "HIDDEN")
        screens.closeMenu()
        expect.that("no on-screen control is shown", !driver.find(By.id("touch.control")).exists())
    }

    /** The touch keyboard: on a dual-screen device it is on the lower screen; otherwise a tap on the game opens it. */
    private fun openKeyboard() {
        val keys = driver.find(By.id("keyboard.key"))
        if (!keys.exists()) {
            driver.find(By.id("game.surface")).tap()
            keys.waitUntilShown()
        }
    }

    @Test
    fun theTouchKeyboardTypesItsKeys() {
        play()
        openKeyboard()
        val keys = driver.find(By.id("keyboard.key")).all().take(3)
        for (key in keys) {
            val code = key.key()!!.toInt()
            val mark = driver.inputMark()
            key.tap()
            driver.waitForInput("key $code", mark) { events -> events.any { it.kind == "key" && it.code == code && it.down } }
            driver.waitForInput("key $code released", mark) { events -> events.any { it.kind == "key" && it.code == code && !it.down } }
        }
    }

    @Test
    fun theTouchpadMovesTheMouse() {
        play()
        openKeyboard()
        val tabs = driver.find(By.id("keyboard.tab"))
        assumeTrue("${app.name}'s touch keyboard has no touchpad", tabs.exists() || driver.find(By.id("keyboard.touchpad")).exists())
        if (!driver.find(By.id("keyboard.touchpad")).exists()) tabs.all().last().tap()
        val pad = driver.find(By.id("keyboard.touchpad")).waitUntilShown()
        val mark = driver.inputMark()
        pad.drag(0.3f, 0.5f, 0.7f, 0.5f, steps = 20)
        val moves = driver.inputSince(mark).filter { it.kind == "move" }
        expect.that("dragging right on the touchpad moves the mouse right", moves.sumOf { it.dx } > 0) { moves.joinToString() }
        val tap = driver.inputMark()
        pad.tap()
        driver.waitForInput("a mouse click", tap) { events -> events.any { it.kind == "button" && it.down } }
    }

    @Test
    fun tappingTheGameInMouseModeClicks() {
        play()
        screens.choose(screens.openGameSettingsInGame(), "game-tap", "mouse")
        screens.closeMenu()
        val mark = driver.inputMark()
        driver.find(By.id("game.surface")).tap()
        driver.waitForInput("a mouse button press", mark) { events -> events.any { it.kind == "button" && it.down } }
    }
}
