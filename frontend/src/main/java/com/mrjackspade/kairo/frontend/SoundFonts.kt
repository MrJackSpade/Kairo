package com.mrjackspade.kairo.frontend

import android.content.Context
import java.io.File

/**
 * The SoundFonts FluidSynth plays a game's General MIDI music with (KairoX86#306, KairoDos#151): GeneralUser
 * GS, which the app ships (shared third_party/generaluser-gs, installed as [SHIPPED_FILE]), and every
 * SoundFont the player has given the app ([library]), kept by content ("sf:<hash>"), which a game picks as
 * its own ([option]).
 */
object SoundFonts {
    /** A game's choice of the SoundFont the app ships. */
    const val DEFAULT = "default"

    /** The shipped SoundFont: its name as the player reads it, and its file. */
    const val SHIPPED_NAME = "GeneralUser GS"
    const val SHIPPED_FILE = "GeneralUser-GS.sf2"

    /** The option's ID, in a game's machine settings. */
    const val ID = "soundFont"

    /** The player's SoundFonts. */
    fun library(context: Context) = ImportedFiles(File(context.filesDir, "soundfonts"), PREFIX, ".sf2", MAX_BYTES,
        context.getString(R.string.kairo_sound_font_too_large), ::validate) { file, importedName ->
        ImportedFiles.Described(importedName ?: file.name)
    }

    /**
     * A game's SoundFont: the shipped one ([DEFAULT]) or one of the player's, which [imported] lists
     * ([choices] of the library); [actions] are the rows that import another (ImportedFilePicker). A catalog
     * reads the option with neither. A picked SoundFont the player has since removed stays the game's
     * setting, and the launch reports it ([file]).
     */
    fun option(imported: () -> List<ChoiceOption> = { emptyList() },
               actions: (select: (String) -> Unit) -> List<SettingsRow> = { emptyList() }) = MachineOption(
        ID, UiText.res(R.string.kairo_option_sound_font), { listOf(ChoiceOption(DEFAULT, UiText.fixed(SHIPPED_NAME))) + imported() },
        DEFAULT, accepts = { it == DEFAULT || it.startsWith("$PREFIX:") }, actions = actions)

    /** The player's SoundFonts as choices of [option]. */
    fun choices(files: ImportedFiles) = files.items().map { ChoiceOption(it.id, UiText.fixed(it.label)) }

    /** The file of a game's SoundFont [value]: [shipped] for [DEFAULT], else the player's; one that is gone fails. */
    fun file(value: String, shipped: File, files: ImportedFiles): File =
        if (value == DEFAULT) shipped else checkNotNull(files.file(value)?.takeIf(File::isFile)) { "No SoundFont $value among the imported ones" }

    /** A SoundFont 2 file is a RIFF file of form "sfbk"; anything else FluidSynth cannot play. */
    private fun validate(file: File) {
        val header = ByteArray(12)
        val read = file.inputStream().use { it.read(header) }
        if (read < 12 || String(header, 0, 4, Charsets.US_ASCII) != "RIFF" || String(header, 8, 4, Charsets.US_ASCII) != "sfbk")
            throw ImportedFiles.Refused(UiText.res(R.string.kairo_sound_font_not_sf2))
    }

    private const val PREFIX = "sf"
    /** The largest SoundFont a player may bring: General MIDI banks run to a few hundred MB. */
    private const val MAX_BYTES = 2L * 1024 * 1024 * 1024
}
