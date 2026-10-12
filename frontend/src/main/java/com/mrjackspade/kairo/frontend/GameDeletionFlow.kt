package com.mrjackspade.kairo.frontend

import android.app.Activity

/** The Delete row on Game settings: write access first, then a red inline confirm. */
object GameDeletionFlow {
    data class Prompt(
        val path: String,
        val targetLabel: String,
        val sourceWarning: String = "",
        val affectedEntries: Int = 1
    )

    fun <T : LibraryItem> row(
        activity: Activity,
        libraryScreen: LibraryScreen<T>,
        prompt: Prompt,
        folder: String,
        writable: () -> Boolean,
        chooseFolder: () -> Unit,
        running: () -> Boolean,
        threadName: String,
        deleteSource: () -> Unit,
        refreshLibrary: () -> Unit
    ): SettingsRow {
        val otherEntries = if (prompt.affectedEntries > 1)
            " This ZIP contains ${prompt.affectedEntries} library entries. All of them will be removed."
        else ""
        return SettingsRow.action("delete", "Delete game ${prompt.targetLabel}", danger = true,
            enabled = { !running() }, disabledReason = { "Exit this game before deleting its source file" },
            tray = {
                if (!writable()) TraySpec("Select the same $folder again and grant write access before deleting game files.",
                    listOf(TrayButton("Cancel"), TrayButton("Choose $folder…", ButtonTone.PRIMARY) { chooseFolder() }), focus = 0)
                else Confirm("Delete ${prompt.path} from device storage? " + prompt.sourceWarning +
                    "This cannot be undone. Game settings and saves are kept." + otherEntries, "Delete", danger = true) {
                    libraryScreen.showStatus("Deleting ${prompt.path}…")
                    Thread {
                        val result = runCatching(deleteSource)
                        activity.runOnUiThread {
                            result.onSuccess {
                                libraryScreen.closeDetail()
                                refreshLibrary()
                            }.onFailure { failure ->
                                // The failure replaces the Deleting status and reaches the message bar.
                                libraryScreen.showStatus(failure.message ?: "Could not delete game file")
                            }
                        }
                    }.apply { name = threadName; start() }
                }.tray()
            })
    }
}
