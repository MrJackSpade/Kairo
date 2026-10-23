package com.mrjackspade.kairo.uitest.suites

import com.mrjackspade.kairo.uitest.By
import com.mrjackspade.kairo.uitest.KairoUiTest
import org.junit.Assume.assumeTrue
import org.junit.Test

/** First run, in every app: the games folder step, then the BIOS step where there is one. */
abstract class FirstRunTests : KairoUiTest() {
    @Test
    fun theFirstStepAsksForTheGamesFolder() {
        val page = screens.reachFolderStep()
        expect.that("the first step offers to choose the games folder", page.find(By.id("footer:choose")).exists())
        expect.that("the first step can be skipped", page.find(By.id("footer:skip")).exists())
    }

    @Test
    fun theControllerStepGivesNoInstructions() {
        val layout = driver.page("Controller layout")
        val folder = screens.setupPage(app.folderStepTitle)
        driver.waitUntil("a setup page") { layout.exists() || folder.exists() }
        assumeTrue("the app did not ask for a controller layout", layout.exists())
        val texts = layout.texts()
        expect.that("the controller step does not explain itself", texts.none { it.startsWith("Choose the layout of your controller") }) {
            texts.joinToString(" | ")
        }
    }

    @Test
    fun choosingTheGamesFolderListsItsGames() {
        screens.completeFirstRun().waitForGame(app.bootGame)
        expect.that("\"${app.bootGame}\" is listed", screens.library.entry(app.bootGame).exists())
    }

    @Test
    fun theBiosStepListsEveryFileAndWhetherItIsRequired() {
        val title = app.biosTitle
        assumeTrue("${app.name} has no BIOS step", title != null)
        val folder = screens.reachFolderStep()
        screens.documents.answerFolder(screens.documents.folder("games")) { folder.find(By.id("footer:choose")).tap() }
        val page = screens.setupPage(title!!).waitUntilShown()
        expect.that("the BIOS step offers to import a whole folder", page.find(By.id("firmware-folder")).exists())
        for (file in app.biosFiles) {
            val row = screens.biosRow(page, file.file)
            val tag = row.find(By.id("tag")).text()
            expect.equal("${file.file}'s mark", if (file.required) "Required" else "Optional", tag)
            expect.that("${file.file} says what it is for", row.find(By.id("description")).text().isNotBlank())
        }
    }

    @Test
    fun importingTheBiosFolderInstallsEveryFile() {
        val title = app.biosTitle
        assumeTrue("${app.name} has no BIOS step", title != null)
        assumeTrue("${app.name}'s tests carry no BIOS files", app.biosResource != null)
        val folder = screens.reachFolderStep()
        screens.documents.answerFolder(screens.documents.folder("games")) { folder.find(By.id("footer:choose")).tap() }
        val page = screens.setupPage(title!!).waitUntilShown()
        screens.importBiosFolder(page)
        for (file in app.biosFiles.filter { it.required }) {
            expect.equal("${file.file} after the import", "Installed", screens.biosRow(page, file.file).find(By.id("value")).text())
        }
    }
}
