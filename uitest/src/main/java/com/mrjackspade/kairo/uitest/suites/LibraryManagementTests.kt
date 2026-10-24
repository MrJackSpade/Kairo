package com.mrjackspade.kairo.uitest.suites

import com.mrjackspade.kairo.uitest.By
import com.mrjackspade.kairo.uitest.KairoUiTest
import org.junit.Test

/**
 * Managing the library, in every app: Refresh finds a game added to the folder, deleting a game
 * removes its file, a game's settings can be reset, and saves can live in a folder of the player's.
 */
abstract class LibraryManagementTests : KairoUiTest() {
    private val spare get() = "spare/" + app.bootGameFile.substringAfterLast('/')

    @Test
    fun refreshFindsAGameAddedToTheFolder() {
        screens.documents.move(app.bootGameFile, spare)
        val library = screens.completeFirstRun().waitForGame(app.otherGame)
        // By file: other entries may share the game's title, as another release of it does.
        expect.that("nothing from ${app.bootGameFile} is listed while the file is elsewhere",
            library.entries().none { it.isFrom(app.bootGameEntryFile) })
        screens.documents.move(spare, app.bootGameFile)
        library.openMenu().find(By.item("f-refresh")).tap()
        expect.that("Refresh lists ${app.bootGameFile}", library.entryFrom(app.bootGameEntryFile).exists())
    }

    @Test
    fun deletingAGameRemovesItsFile() {
        val library = screens.completeFirstRun().waitForGame(app.bootGame)
        val settings = library.open(library.entryFrom(app.bootGameEntryFile)).openSettings()
        settings.find(By.item("delete")).tap()
        screens.confirm()
        driver.waitUntil("the game file to be deleted") { !screens.documents.exists(app.bootGameFile) }
        library.waitUntilShown()
        driver.waitUntil("${app.bootGameFile} to leave the library") {
            library.entries().none { it.isFrom(app.bootGameEntryFile) }
        }
    }

    @Test
    fun resettingAGamesSettingsRestoresTheDefaults() {
        val library = screens.completeFirstRun().waitForGame(app.bootGame)
        val settings = library.open(app.bootGame).openSettings()
        screens.choose(settings, "game-tap", "mouse")
        expect.equal("Screen tap after choosing Mouse", "Mouse", settings.find(By.item("game-tap")).find(By.id("value")).text())
        settings.find(By.item("reset")).tap()
        screens.confirm()
        driver.waitUntil("Screen tap to leave Mouse") { settings.find(By.item("game-tap")).find(By.id("value")).textNow() != "Mouse" }
    }

    @Test
    fun savesGoToAChosenFolder() {
        org.junit.Assume.assumeTrue("${app.name} has no saved states", app.hasStates)
        val library = screens.completeFirstRun().waitForGame(app.bootGame)
        library.openMenu().find(By.item("f-configurations")).tap()
        val configurations = driver.page("Configurations").waitUntilShown()
        screens.documents.answerFolder(screens.documents.folder("saves")) { screens.choose(configurations, "save-folder", "folder") }
        driver.waitUntil("the save folder to be the chosen one") {
            configurations.find(By.item("save-folder")).find(By.id("value")).textNow()?.contains("saves") == true
        }
        screens.closeMenu()
        library.open(app.bootGame).play()
        driver.waitFrames(SETTLE_FRAMES)
        screens.saveState(1)
        driver.waitUntil("a save in the chosen folder") { screens.documents.files("saves").isNotEmpty() }
        driver.log.step("files", screens.documents.files("saves").joinToString())
    }
}

/**
 * Settings, in every app: a machine default applies to games, a game's own setting overrides it,
 * and Sound switches the game's sound.
 */
abstract class SettingsTests : KairoUiTest() {
    @Test
    fun aMachineDefaultAppliesToGames() {
        val option = app.defaultOption
        org.junit.Assume.assumeTrue("${app.name} has no machine option to change", option != null)
        val library = screens.completeFirstRun().waitForGame(app.bootGame)
        val picked = screens.choose(screens.openMachinePage(), "default-$option")
        screens.closeMenu()
        library.open(app.bootGame).play()
        expect.equal("${app.bootGame}'s $option", picked, driver.probe().details["option.$option"])
    }

    @Test
    fun aGamesOwnSettingOverridesTheDefault() {
        val option = app.defaultOption
        org.junit.Assume.assumeTrue("${app.name} has no machine option to change", option != null)
        val library = screens.completeFirstRun().waitForGame(app.bootGame)
        val picked = screens.choose(library.open(app.bootGame).openSettings(), option!!)
        driver.press(android.view.KeyEvent.KEYCODE_BUTTON_B)
        com.mrjackspade.kairo.uitest.GamePage(driver, app).play()
        expect.equal("${app.bootGame}'s own $option", picked, driver.probe().details["option.$option"])
        // The picked value differs from the default (it was not the chosen one), so the other game,
        // which keeps the default, starts with something else.
        screens.endGame().open(app.otherGame).play()
        val other = driver.probe().details["option.$option"]
        expect.that("${app.otherGame} keeps the default $option, not ${app.bootGame}'s $picked", other != picked) { other.orEmpty() }
    }

    @Test
    fun aSettingIsKeptWhenTheAppStartsAgain() {
        screens.completeFirstRun()
        screens.library.openMenu().find(By.item("f-controls")).tap()
        screens.choose(driver.page("Controls").waitUntilShown(), "osc", "HIDDEN")
        session.relaunch()
        screens.library.waitUntilShown().openMenu().find(By.item("f-controls")).tap()
        expect.equal("On-screen controls after restarting", "Hidden",
            driver.page("Controls").waitUntilShown().find(By.item("osc")).find(By.id("value")).text())
    }

    @Test
    fun soundSwitchesOffAndOn() {
        screens.completeFirstRun().open(app.bootGame).play()
        driver.waitFrames(SETTLE_FRAMES)
        val menu = screens.openGameMenu()
        val sound = menu.find(By.item("f-sound"))
        val before = sound.key()
        sound.tap()
        expect.that("Sound changes when switched", sound.key() != before) { "${sound.key()} after $before" }
        sound.tap()
        expect.equal("Sound after switching back", before, sound.key())
    }
}
