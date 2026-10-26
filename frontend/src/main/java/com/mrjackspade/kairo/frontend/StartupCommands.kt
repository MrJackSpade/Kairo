package com.mrjackspade.kairo.frontend

/**
 * A game's startup commands as a machine's launch takes them: the shared frame host types the
 * lines at the guest's prompt (cpp/kairo/startup_typer.h, machine_jni.cpp).
 */
object StartupCommands {
    /** The launch setting the frame host reads them from. */
    const val SETTING = "commands"

    /** The launch setting for [lines]; none types nothing. */
    fun setting(lines: List<String>): Pair<String, String> {
        require(lines.none { '\n' in it }) { "A startup command holds a line break" }
        return SETTING to lines.joinToString("\n")
    }
}
