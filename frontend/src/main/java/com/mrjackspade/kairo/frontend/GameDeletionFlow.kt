package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.app.AlertDialog

/** Confirms source deletion and coordinates its library UI result. */
object GameDeletionFlow {
    data class Prompt(
        val path: String,
        val targetLabel: String,
        val sourceWarning: String = "",
        val affectedEntries: Int = 1
    )

    fun <T : LibraryItem> show(
        activity: Activity,
        libraryScreen: LibraryScreen<T>,
        prompt: Prompt,
        threadName: String,
        deleteSource: () -> Unit,
        refreshLibrary: () -> Unit,
        reportFailure: (String) -> Unit
    ) {
        val otherEntries = if (prompt.affectedEntries > 1)
            "\n\nThis ZIP contains ${prompt.affectedEntries} library entries. All of them will be removed."
        else ""
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Permanently delete game ${prompt.targetLabel}?")
            .setMessage("Delete ${prompt.path} from device storage?\n\n" +
                prompt.sourceWarning +
                "This cannot be undone. Game settings and saves are kept." + otherEntries)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                libraryScreen.showStatus("Deleting ${prompt.path}…")
                Thread {
                    val result = runCatching(deleteSource)
                    activity.runOnUiThread {
                        result.onSuccess {
                            libraryScreen.closeDetail()
                            refreshLibrary()
                        }.onFailure { failure ->
                            reportFailure(failure.message ?: "Could not delete game file")
                        }
                    }
                }.apply { name = threadName; start() }
            }.create()
        dialog.show()
        Ui.styleDialog(dialog)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Ui.DANGER)
    }
}
