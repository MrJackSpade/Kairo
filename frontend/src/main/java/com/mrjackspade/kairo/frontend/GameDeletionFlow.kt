package com.mrjackspade.kairo.frontend

import android.app.Activity

/** The Delete row on Game settings: write access first, then a red inline confirm. */
object GameDeletionFlow {
    data class Prompt(
        val path: String,
        val targetLabel: String,
        val sourceWarning: String = ""
    )

    /**
     * [deleteSource] removes [entry]'s document and anything the app cached for it; the library
     * then drops every entry read from that document without rescanning the folder.
     */
    fun <T : LibraryItem> row(
        activity: Activity,
        library: LibraryFlow<T>,
        entry: T,
        prompt: Prompt,
        folder: String,
        running: () -> Boolean,
        threadName: String,
        deleteSource: () -> Unit
    ): SettingsRow {
        val affectedEntries = library.entries.count { it.uri == entry.uri }
        val otherEntries = if (affectedEntries > 1)
            " This ZIP contains $affectedEntries library entries. All of them will be removed."
        else ""
        val screen = library.screen
        return SettingsRow.action("delete", "Delete game ${prompt.targetLabel}", danger = true,
            enabled = { !running() }, disabledReason = { "Exit this game before deleting its source file" },
            tray = {
                if (library.tree?.let(library::hasWriteGrant) != true) TraySpec("Select the same $folder again and grant write access before deleting game files.",
                    listOf(TrayButton("Cancel"), TrayButton("Choose $folder…", ButtonTone.PRIMARY) { library.chooseFolder() }), focus = 0)
                else Confirm("Delete ${prompt.path} from device storage? " + prompt.sourceWarning +
                    "This cannot be undone. Game settings and saves are kept." + otherEntries, "Delete", danger = true) {
                    screen.showStatus("Deleting ${prompt.path}…")
                    Thread {
                        val result = runCatching(deleteSource)
                        activity.runOnUiThread {
                            result.onSuccess {
                                screen.closeDetail()
                                library.removeSource(entry)
                            }.onFailure { failure ->
                                // The failure replaces the Deleting status and reaches the message bar.
                                screen.showStatus(failure.message ?: "Could not delete game file")
                            }
                        }
                    }.apply { name = threadName; start() }
                }.tray()
            })
    }
}
