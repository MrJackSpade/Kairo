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

    /** [bootGame]'s file in the test resources, such as "games/Acrojet.zip", for launches from a frontend. */
    abstract val bootGameFile: String

    /** Another game, for checking that one game's settings leave others alone. */
    abstract val otherGame: String

    /**
     * A machine option (MachineOption id) whose default the tests may change and that
     * [bootGame]'s catalog record leaves alone; null for an app without such options.
     */
    open val defaultOption: String? = null

    /** Games whose disks, dumps and other releases must show as exactly one library entry each. */
    open val singleEntryGames: List<String> = emptyList()

    /** Games that must stay separate entries, as different releases in their own folders. */
    open val separateGames: List<String> = emptyList()

    /** File names (or parts of them) of things that are not games and must never be listed. */
    open val hiddenFiles: List<String> = emptyList()

    /** A word that finds [bootGame] in library search and leaves out other games. */
    abstract val searchTerm: String

    /**
     * The guest's cursor keys, up, down, left and right, as guest key codes: the D-pad reaches them
     * by default in every app (Kairo#37).
     */
    abstract val arrowKeys: List<Int>

    /** The guest's Return and Space keys, which A and B send by default in every app (Kairo#37). */
    abstract val returnKey: Int
    abstract val spaceKey: Int

    /** Whether the app saves and loads states (KairoDos, on DOSBox Staging, has none). */
    open val hasStates = true

    /**
     * A disk change to try while [MediaSwap.game] runs: its media page row for [MediaSwap.slot]
     * ("Drive 2", "Floppy A", "Cartridge B") takes the library disk whose name contains
     * [MediaSwap.insert]; null for an app without media pages.
     */
    open val mediaSwap: MediaSwap? = null

    class MediaSwap(val game: String, val slot: String, val insert: String)

    class BiosFile(val file: String, val required: Boolean)
}
