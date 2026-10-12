package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import java.text.DateFormat
import java.util.Date

/**
 * Configurations › Save folder: app storage or a folder the user picks. Changing it moves
 * every save; a save already in the destination is a conflict the user resolves first.
 */
class SaveFolderSettings(
    private val activity: Activity,
    private val pages: PageStack,
    private val preferences: SharedPreferences,
    val app: AppSaveLocation,
    private val names: Set<String>,
    private val gameTitle: (String) -> String,
    private val unitLabel: (String) -> String,
    /** Why the folder cannot change now, such as a running game; null when it can. */
    private val blocked: () -> String?,
    /** Runs on a worker before saves move, such as converting an older save format. */
    private val prepare: ((String) -> Unit) -> Unit = {}
) {
    /** The chosen folder, or null for app storage. */
    val folder: FolderSaveLocation? get() = preferences.getString(PREFERENCE, null)?.let(Uri::parse)?.let { uri ->
        check(activity.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission && it.isWritePermission
        }) { "Access to the save folder was lost. Choose it again in Configurations › Save folder." }
        FolderSaveLocation(activity.contentResolver, uri)
    }

    val location: SaveLocation get() = folder ?: app

    /** True while saves are moving. Games must not start or save until it finishes. */
    @Volatile var busy = false
        private set
    @Volatile private var status: String? = null

    private val label: String? get() = preferences.getString(PREFERENCE, null)?.let { stored ->
        DocumentsContract.getTreeDocumentId(Uri.parse(stored)).substringAfterLast('/').substringAfterLast(':')
    }

    fun row() = SettingsRow.choice("save-folder", "Save folder", ChoiceSpec("Save folder",
        { GameSource.ALL_GAMES }, {
            listOf(ChoiceGroup(null, listOf(ChoiceOption(PRIVATE, "Private app storage"),
                ChoiceOption(FOLDER, "Custom folder"))))
        }, { if (label == null) PRIVATE else FOLDER }, select = { id ->
            if (id == FOLDER) chooseFolder()
            else if (label != null) change(app) { check(preferences.edit().remove(PREFERENCE).commit()) {
                "Could not remember the save folder"
            } }
        }, cycles = false), value = { status ?: label ?: "Private app storage" },
        enabled = { !busy && blocked() == null },
        disabledReason = { if (busy) "Wait for the saves to finish moving." else blocked() })

    /** Returns true when the result belongs to the save folder picker. */
    fun handleActivityResult(code: Int, result: Int, data: Intent?): Boolean {
        if (code != REQUEST) return false
        val uri = data?.data
        if (result != Activity.RESULT_OK || uri == null) return true
        if (uri.toString() == preferences.getString(PREFERENCE, null)) return true
        activity.contentResolver.takePersistableUriPermission(uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        change(FolderSaveLocation(activity.contentResolver, uri)) {
            check(preferences.edit().putString(PREFERENCE, uri.toString()).commit()) {
                "Could not remember the save folder"
            }
        }
        return true
    }

    private fun chooseFolder() {
        @Suppress("DEPRECATION")
        activity.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION), REQUEST)
    }

    private fun change(target: SaveLocation, remember: () -> Unit) {
        blocked()?.let { Ui.message(activity, it); return }
        busy = true
        show("Checking saves…")
        Thread({
            val result = runCatching {
                prepare(::show)
                val migration = SaveMigration(location, target, names, activity.cacheDir)
                migration to migration.conflicts()
            }
            activity.runOnUiThread {
                result.fold({ (migration, conflicts) ->
                    if (conflicts.isEmpty()) move(migration, emptySet(), remember)
                    else pages.push(conflictPage(migration, conflicts, target === app, remember))
                }, { error -> finish("Could not change the save folder: ${error.message ?: "storage error"}") })
            }
        }, "Kairo-save-folder").start()
    }

    private fun move(migration: SaveMigration, keepNew: Set<SaveUnit>, remember: () -> Unit) {
        show("Moving saves…")
        Thread({
            val result = runCatching {
                migration.run(keepNew, { done, total -> show("Moving $done of $total…") }, remember)
            }
            activity.runOnUiThread {
                finish(result.fold({ "Save folder changed" },
                    { error -> "Could not move saves: ${error.message ?: "storage error"}" }))
            }
        }, "Kairo-save-folder").start()
    }

    private fun show(text: String) {
        status = text
        activity.runOnUiThread { pages.refresh() }
    }

    private fun finish(message: String) {
        busy = false
        status = null
        pages.refresh()
        Ui.message(activity, message, long = true)
    }

    /** Lists each save in both places and lets the user keep one copy of each. */
    private fun conflictPage(migration: SaveMigration, conflicts: List<SaveMigration.Conflict>,
                             toPrivate: Boolean, remember: () -> Unit): GroupsPage {
        val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        val keep = linkedMapOf<SaveUnit, String?>().apply { conflicts.forEach { put(it.unit, null) } }
        val newName = if (toPrivate) "Private app storage" else "New folder"
        var moving = false
        val rows = conflicts.map { conflict ->
            val unit = conflict.unit
            val title = "${gameTitle(unit.game)} \u00b7 ${unitLabel(unit.name)}"
            SettingsRow.choice("conflict-${unit.game}-${unit.name}", title, ChoiceSpec(title,
                { GameSource.ALL_GAMES }, {
                    listOf(ChoiceGroup(null, listOf(
                        ChoiceOption(CURRENT, "Current", format.format(Date(conflict.currentSavedAt))),
                        ChoiceOption(NEW, newName, format.format(Date(conflict.newSavedAt))))))
                }, { keep[unit] }, select = { keep[unit] = it }, cycles = false),
                value = { when (keep[unit]) { CURRENT -> "Current"; NEW -> newName; else -> "Choose" } },
                tone = { if (keep[unit] == null) ValueTone.EMPTY else ValueTone.CURRENT })
        }
        return object : GroupsPage("Save conflicts", { GameSource.ALL_GAMES }, { listOf(RowGroup("KEEP", rows)) }, {
            listOf(
                FooterButton("cancel", { "Cancel" }) { pages.pop() },
                FooterButton("move", { "Move saves" }, ButtonTone.PRIMARY, { keep.values.all { it != null } },
                    "Choose which save to keep for each game.") {
                    moving = true
                    pages.pop()
                    move(migration, keep.filterValues { it == NEW }.keys, remember)
                })
        }) {
            override fun onRemoved() {
                super.onRemoved()
                if (!moving) finish("Save folder not changed")
            }
        }
    }
    companion object {
        private const val PREFERENCE = "save_folder"
        private const val REQUEST = 48130
        private const val PRIVATE = "private"
        private const val FOLDER = "folder"
        private const val CURRENT = "current"
        private const val NEW = "new"
    }
}
