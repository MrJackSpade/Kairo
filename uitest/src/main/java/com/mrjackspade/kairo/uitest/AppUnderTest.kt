package com.mrjackspade.kairo.uitest

/**
 * What the shared tests need to know about one app, which its androidTest supplies
 * (docs/ui-tests.md). Titles are as the library shows them, from the app's catalog; files are
 * names inside the test resources' games folder.
 */
abstract class AppUnderTest {
    /** The product name, as in "Exit Kairo88". */
    abstract val name: String

    /** The library header, "KAIRO" and the product's own part. */
    abstract val brand: String

    /** The first-run step that asks for the games folder. */
    open val folderStepTitle = "Choose a games folder"

    /**
     * The BIOS step and Machine page title, such as "PC-88 BIOS"; null for an app without
     * firmware, which has no such step.
     */
    abstract val biosTitle: String?

    /** Every BIOS file the page lists, in order, and whether it is required. */
    open val biosFiles: List<BiosFile> = emptyList()

    /** The test resources' folder of BIOS files; null when the tests use none, as with a built-in BIOS. */
    open val biosResource: String? = "bios"

    /** A game that starts with the test BIOS and shows a picture. */
    abstract val bootGame: String

    /** Games whose disks, dumps and other releases must show as exactly one library entry each. */
    open val singleEntryGames: List<String> = emptyList()

    /** Games that must stay separate entries, as different releases in their own folders. */
    open val separateGames: List<String> = emptyList()

    /** File names (or parts of them) of things that are not games and must never be listed. */
    open val hiddenFiles: List<String> = emptyList()

    /** A word that finds [bootGame] in library search and leaves out other games. */
    abstract val searchTerm: String

    class BiosFile(val file: String, val required: Boolean)
}
