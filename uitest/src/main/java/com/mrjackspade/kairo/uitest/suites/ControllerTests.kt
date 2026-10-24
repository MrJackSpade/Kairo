package com.mrjackspade.kairo.uitest.suites

import android.view.KeyEvent
import com.mrjackspade.kairo.uitest.By
import com.mrjackspade.kairo.uitest.KairoUiTest
import org.junit.Test

/**
 * The controller, in every app: its buttons reach the game, mapping a button changes what it
 * sends, a game's own mapping stays with that game, and Reset restores the defaults. What the game
 * receives is read from the app's record of guest input (GuestInputRecord).
 */
abstract class ControllerTests : KairoUiTest() {
    /** The controller buttons every app's default mapping gives the game something for. */
    protected open val mappedButtons = listOf(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B,
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_START)

    private fun play() {
        screens.completeFirstRun().open(app.bootGame).play()
        driver.waitFrames(SETTLE_FRAMES)
    }

    /** What pressing [button] sends the guest: its events, pressed and released. */
    private fun sends(button: Int): List<String> {
        val mark = driver.inputMark()
        driver.press(button)
        return driver.inputSince(mark).map { "${it.kind} ${it.code} ${if (it.down) "down" else "up"}" }
    }

    @Test
    fun everyMappedButtonReachesTheGame() {
        play()
        for (button in mappedButtons) {
            val events = sends(button)
            expect.that("${KeyEvent.keyCodeToString(button)} sends the game something", events.isNotEmpty())
            expect.that("${KeyEvent.keyCodeToString(button)} is released when let go",
                events.count { it.endsWith("down") } == events.count { it.endsWith("up") }) { events.joinToString() }
        }
    }

    @Test
    fun aIsReturnAndBIsSpace() {
        play()
        val a = sends(KeyEvent.KEYCODE_BUTTON_A)
        expect.that("A sends Return (key ${app.returnKey})", a.contains("key ${app.returnKey} down")) { a.joinToString() }
        val b = sends(KeyEvent.KEYCODE_BUTTON_B)
        expect.that("B sends Space (key ${app.spaceKey})", b.contains("key ${app.spaceKey} down")) { b.joinToString() }
    }

    @Test
    fun theDPadReachesTheArrowKeys() {
        play()
        val pad = listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)
        for ((button, key) in pad.zip(app.arrowKeys)) {
            val events = sends(button)
            expect.that("${KeyEvent.keyCodeToString(button)} sends cursor key $key", events.contains("key $key down")) { events.joinToString() }
        }
    }

    @Test
    fun theControlsPageNamesTheGamesOwnMapping() {
        play()
        screens.openGameMenu().find(By.item("f-controls")).tap()
        var controls = driver.page("Controls").waitUntilShown()
        expect.that("a game on the all-games mapping has no row of its own", !controls.find(By.item("running-mapping")).exists())
        expect.equal("the mapping row of a game on the all-games mapping", "Button mapping",
            controls.find(By.item("mapping")).find(By.id("label")).text())
        screens.closeMenu()
        screens.openGameSettingsInGame().find(By.item("game-mapping")).tap()
        screens.mapToFirstKey(driver.page("Button mapping").waitUntilShown(), "a")
        screens.closeMenu()
        screens.openGameMenu().find(By.item("f-controls")).tap()
        controls = driver.page("Controls").waitUntilShown()
        val own = controls.find(By.item("running-mapping"))
        expect.equal("the running game's own mapping row", "${app.runningTitle} mapping", own.find(By.id("label")).text())
        expect.equal("its value", "Custom", own.find(By.id("value")).text())
        expect.equal("the all-games row beside it", "All-games mapping", controls.find(By.item("mapping")).find(By.id("label")).text())
        expect.equal("the all-games row's value", "Built-in", controls.find(By.item("mapping")).find(By.id("value")).text())
    }

    @Test
    fun mappingAButtonToAKeySendsThatKey() {
        play()
        screens.openGameMenu().find(By.item("f-controls")).tap()
        driver.page("Controls").waitUntilShown().find(By.item("mapping")).tap()
        val code = screens.mapToFirstKey(driver.page("Button mapping").waitUntilShown(), "a")
        screens.closeMenu()
        expect.that("A sends key $code", sends(KeyEvent.KEYCODE_BUTTON_A).contains("key $code down"))
    }

    @Test
    fun aGamesOwnMappingStaysWithThatGame() {
        val library = screens.completeFirstRun()
        val settings = library.open(app.bootGame).openSettings()
        settings.find(By.item("game-mapping")).tap()
        val code = screens.mapToFirstKey(driver.page("Button mapping").waitUntilShown(), "a")
        driver.press(KeyEvent.KEYCODE_BUTTON_B)
        settings.waitUntilShown().find(By.id("page.back")).tap()
        com.mrjackspade.kairo.uitest.GamePage(driver, app, app.bootGame).play()
        driver.waitFrames(SETTLE_FRAMES)
        expect.that("in ${app.bootGame}, A sends key $code", sends(KeyEvent.KEYCODE_BUTTON_A).contains("key $code down"))
        screens.endGame().open(app.otherGame).play()
        driver.waitFrames(SETTLE_FRAMES)
        expect.that("in ${app.otherGame}, A does not send key $code", !sends(KeyEvent.KEYCODE_BUTTON_A).contains("key $code down"))
    }

    @Test
    fun resetRestoresTheDefaultMapping() {
        play()
        val before = sends(KeyEvent.KEYCODE_BUTTON_A)
        screens.openGameMenu().find(By.item("f-controls")).tap()
        driver.page("Controls").waitUntilShown().find(By.item("mapping")).tap()
        val mapping = driver.page("Button mapping").waitUntilShown()
        screens.mapToFirstKey(mapping, "a")
        mapping.find(By.item("m:reset")).tap()
        screens.confirm()
        screens.closeMenu()
        expect.equal("what A sends after Reset to defaults", before, sends(KeyEvent.KEYCODE_BUTTON_A))
    }

    /** Frames a second the running game draws over [millis], optionally while [button] is held. */
    private fun frameRate(millis: Long, button: Int? = null): Double {
        val start = driver.probeNow().details.getValue("frames").toLong()
        val time = android.os.SystemClock.elapsedRealtime()
        if (button == null) Thread.sleep(millis) else driver.press(button, holdMillis = millis)
        val frames = driver.probeNow().details.getValue("frames").toLong() - start
        return frames * 1000.0 / (android.os.SystemClock.elapsedRealtime() - time)
    }

    @Test
    fun aFastForwardButtonSpeedsUpTheGame() {
        play()
        screens.openGameMenu().find(By.item("f-controls")).tap()
        driver.page("Controls").waitUntilShown().find(By.item("mapping")).tap()
        driver.page("Button mapping").waitUntilShown().find(By.item("m:r1")).tap()
        val target = driver.page("Button target").waitUntilShown()
        target.find(By.item("tab:ACTION")).tap()
        target.find(By.item("action:fastForward")).tap()
        target.find(By.id("footer:save")).tap()
        target.waitUntilGone()
        screens.closeMenu()
        val normal = frameRate(2000)
        val fast = frameRate(2000, KeyEvent.KEYCODE_BUTTON_R1)
        driver.log.step("measure", "%.1f frames a second normally, %.1f with R1 held".format(normal, fast))
        expect.that("holding the fast-forward button runs the game faster", fast > normal * 1.3) { "%.1f against %.1f".format(fast, normal) }
        val after = frameRate(2000)
        expect.that("letting go returns to normal speed", after < normal * 1.15) { "%.1f against %.1f".format(after, normal) }
    }

    @Test
    fun theControllerDrivesTheMenus() {
        val library = screens.completeFirstRun()
        library.openMenu()
        driver.press(KeyEvent.KEYCODE_DPAD_DOWN)
        driver.press(KeyEvent.KEYCODE_BUTTON_B)
        driver.page("flyout").waitUntilGone()
        expect.that("B closes the library menu", !driver.page("flyout").exists())
        library.open(app.bootGame)
        driver.press(KeyEvent.KEYCODE_BUTTON_A) // Play is focused on a playable game's page.
        driver.waitForGame("Running")
        expect.equal("the game A started", app.runningTitle, driver.probe().title)
    }
}
