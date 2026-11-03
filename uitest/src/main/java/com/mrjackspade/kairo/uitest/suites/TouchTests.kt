package com.mrjackspade.kairo.uitest.suites

import android.os.SystemClock
import com.mrjackspade.kairo.frontend.GuestKeyboardPanel
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

    /**
     * Kairo#31: a touch on the keyboard leaves no part of it holding focus, so the controller's
     * buttons go on reaching the game instead of lighting up the keyboard's own buttons.
     */
    @Test
    fun touchingTheKeyboardLeavesTheControllerWithTheGame() {
        play()
        openKeyboard()
        driver.find(By.id("keyboard.key")).nth(0).tap()
        val focusedInKeyboard = driver.onMain {
            driver.roots().mapNotNull { it.findFocus() }.filter { view ->
                generateSequence(view) { it.parent as? android.view.View }
                    .any { com.mrjackspade.kairo.frontend.TestAttributes.id(it)?.startsWith("keyboard.") == true }
            }.map { com.mrjackspade.kairo.frontend.TestAttributes.id(it) ?: it.javaClass.simpleName }
        }
        expect.that("nothing in the touch keyboard holds focus after a touch", focusedInKeyboard.isEmpty()) { focusedInKeyboard.joinToString() }
        val mark = driver.inputMark()
        driver.press(android.view.KeyEvent.KEYCODE_BUTTON_A)
        expect.that("A still reaches the game", driver.inputSince(mark).isNotEmpty())
    }

    @Test
    fun theJoystickTabDrivesTheJoystick() {
        play()
        openKeyboard()
        driver.find(By.id("keyboard.tab").withKey(GuestKeyboardPanel.JOYSTICK_PAGE.toString())).tap()
        val controls = driver.find(By.id("joystick.control"))
        controls.waitUntilShown()
        for (control in controls.all()) {
            val mark = driver.inputMark()
            control.tap()
            driver.waitForInput("joystick ${control.key()} pressed", mark) { events -> events.any { it.kind == "joystick" && it.down } }
            driver.waitForInput("joystick ${control.key()} released", mark) { events -> events.any { it.kind == "joystick" && !it.down } }
        }
    }

    @Test
    fun theTouchpadMovesTheMouse() {
        play()
        openKeyboard()
        val tabs = driver.find(By.id("keyboard.tab"))
        assumeTrue("${app.name}'s touch keyboard has no touchpad", tabs.exists() || driver.find(By.id("keyboard.touchpad")).exists())
        if (!driver.find(By.id("keyboard.touchpad")).exists())
            driver.find(By.id("keyboard.tab").withKey(GuestKeyboardPanel.TOUCHPAD_PAGE.toString())).tap()
        val pad = driver.find(By.id("keyboard.touchpad")).waitUntilShown()
        val mark = driver.inputMark()
        pad.drag(0.3f, 0.5f, 0.7f, 0.5f, steps = 20)
        val moves = driver.inputSince(mark).filter { it.kind == "move" }
        expect.that("dragging right on the touchpad moves the mouse right", moves.sumOf { it.dx } > 0) { moves.joinToString() }
        val tap = driver.inputMark()
        pad.tap()
        driver.waitForInput("a mouse click", tap) { events -> events.any { it.kind == "button" && it.down } }
    }

    /** Plays [game] with Screen tap on Auto, which decides from what the game reads (Kairo#78). */
    private fun playOnAuto(game: String) {
        screens.completeFirstRun().open(game).play()
        screens.choose(screens.openGameSettingsInGame(), "game-tap", "auto")
        screens.closeMenu()
    }

    private fun touchMode() = driver.probeNow().details["touch.mode"]

    /**
     * Kairo#78, KairoTowns#82, Kairo#104: in a game that reads the mouse, Auto makes the screen the
     * mouse: a drag moves the guest's pointer, and a tap opens no keyboard.
     */
    @Test
    fun autoTouchMovesTheMouseInAMouseGame() {
        val game = requireNotNull(app.mouseGame) { "${app.name}'s test resources name no mouse game (AppUnderTest.mouseGame)" }
        playOnAuto(game)
        // Keys the game asks for first go in again every two seconds, as a player answers a prompt
        // that may not be up yet: one pressed before the guest reads it is lost.
        var nextKeys = 0L
        driver.waitUntil("Auto to find $game reading the mouse") {
            if (app.mouseGameKeys.isNotEmpty() && SystemClock.uptimeMillis() >= nextKeys) {
                app.mouseGameKeys.forEach { driver.press(it, fromKeyboard = true) }
                nextKeys = SystemClock.uptimeMillis() + 2000
            }
            touchMode() == "mouse"
        }
        val surface = driver.find(By.id("game.surface"))
        val mark = driver.inputMark()
        surface.drag(0.4f, 0.5f, 0.6f, 0.5f, steps = 20)
        val moves = driver.inputSince(mark).filter { it.kind == "move" }
        expect.that("a drag moves the guest's pointer", moves.isNotEmpty()) { driver.inputSince(mark).joinToString() }
        val keyboardBefore = driver.find(By.id("keyboard.key")).exists()
        surface.tap()
        driver.waitFrames(30)
        expect.that("a tap opens no keyboard", keyboardBefore || !driver.find(By.id("keyboard.key")).exists())
    }

    /** Kairo#104: in a game played with the keyboard or a pad, Auto opens the keyboard on a tap. */
    @Test
    fun autoTouchOpensTheKeyboardInAKeyboardGame() {
        val game = requireNotNull(app.keyboardGame) { "${app.name}'s test resources name no keyboard game (AppUnderTest.keyboardGame)" }
        playOnAuto(game)
        driver.waitFrames(SETTLE_FRAMES)
        expect.equal("what Auto makes of $game", "keyboard", touchMode())
        driver.find(By.id("game.surface")).tap()
        driver.find(By.id("keyboard.key")).waitUntilShown()
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
