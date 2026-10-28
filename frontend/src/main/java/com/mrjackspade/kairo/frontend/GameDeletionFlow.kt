package com.mrjackspade.kairo.frontend

import android.app.Activity

/** The Delete row on Game settings: write access first, then a red inline confirm. */
object GameDeletionFlow {
    /** Deleting the game at [path]: its file, or when [folder], its folder and everything inside it. */
    data class Prompt(
        val path: String,
        val folder: Boolean = false
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
        val question = listOfNotNull(
            activity.getString(R.string.kairo_delete_question, prompt.path),
            if (prompt.folder) activity.getString(R.string.kairo_delete_folder_warning) else null,
            activity.getString(R.string.kairo_delete_permanent),
            if (affectedEntries > 1) activity.resources.getQuantityString(R.plurals.kairo_delete_zip_entries,
                affectedEntries, affectedEntries) else null
        ).joinToString(" ")
        val screen = library.screen
        return SettingsRow.action("delete",
            activity.getString(if (prompt.folder) R.string.kairo_delete_game_folder else R.string.kairo_delete_game_file), danger = true,
            enabled = { !running() }, disabledReason = { activity.getString(R.string.kairo_delete_exit_first) },
            tray = {
                if (library.tree?.let(library::hasWriteGrant) != true) TraySpec(activity.getString(R.string.kairo_delete_grant, folder),
                    listOf(TrayButton(activity.getString(R.string.kairo_cancel)),
                        TrayButton(activity.getString(R.string.kairo_choose_named, folder), ButtonTone.PRIMARY) { library.chooseFolder() }), focus = 0)
                else Confirm(question, activity.getString(R.string.kairo_delete), danger = true) {
                    screen.showStatus(activity.getString(R.string.kairo_deleting, prompt.path), LibraryScreen.StatusKind.TASK)
                    Thread {
                        val result = runCatching(deleteSource)
                        activity.runOnUiThread {
                            result.onSuccess {
                                screen.closeDetail()
                                library.removeSource(entry)
                            }.onFailure { failure ->
                                // The failure replaces the Deleting status and reaches the message bar.
                                screen.showStatus(failure.message ?: activity.getString(R.string.kairo_delete_failed))
                            }
                        }
                    }.apply { name = threadName; start() }
                }.tray(activity)
            })
    }
}
