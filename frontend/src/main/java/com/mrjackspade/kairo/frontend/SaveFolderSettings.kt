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
        val newName = if (toPrivate) "Private app storage" else "New folder"
        var moving = false
        return choicePage(conflicts.map { conflict ->
            Choice(conflict.unit, "${gameTitle(conflict.unit.game)} · ${unitLabel(conflict.unit.name)}",
                "Current", conflict.currentSavedAt, newName, conflict.newSavedAt)
        }, "Move saves", { moving = true; move(migration, it, remember) }) {
            if (!moving) finish("Save folder not changed")
        }
    }

    /**
     * Asks which copy to keep of each save that a game has from an earlier version and under its
     * key, as when another device sharing the folder moved it first. Blocks the calling worker
     * until the user chooses; returns the conflicts whose earlier copy is kept.
     */
    fun resolveMigrationConflicts(conflicts: List<GameKeyMigration.SaveStore.Conflict>,
                                  title: (GameKey) -> String): Set<GameKeyMigration.SaveStore.Conflict> {
        val latch = java.util.concurrent.CountDownLatch(1)
        var chosen: Set<GameKeyMigration.SaveStore.Conflict>? = null
        activity.runOnUiThread {
            pages.push(choicePage(conflicts.map { conflict ->
                Choice(conflict, "${title(conflict.game)} · ${unitLabel(conflict.unit)}",
                    "Earlier version", conflict.earlierSavedAt, "This version", conflict.keyedSavedAt)
            }, "Keep saves", { keptNewer -> chosen = conflicts.toSet() - keptNewer; latch.countDown() }) {
                latch.countDown()
            })
        }
        latch.await()
        return checkNotNull(chosen) { "Saves were not moved; choose which to keep the next time the library opens" }
    }

    private class Choice<K>(val id: K, val title: String, val currentName: String, val currentSavedAt: Long,
                            val otherName: String, val otherSavedAt: Long)

    /** One row per save: keep the current copy or the other one. [confirm] receives the saves whose other copy wins. */
    private fun <K> choicePage(choices: List<Choice<K>>, confirmLabel: String, confirm: (Set<K>) -> Unit,
                               cancelled: () -> Unit): GroupsPage {
        val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        val keep = linkedMapOf<K, String?>().apply { choices.forEach { put(it.id, null) } }
        var confirmed = false
        val rows = choices.mapIndexed { index, choice ->
            SettingsRow.choice("conflict-$index", choice.title, ChoiceSpec(choice.title,
                { GameSource.ALL_GAMES }, {
                    listOf(ChoiceGroup(null, listOf(
                        ChoiceOption(CURRENT, choice.currentName, format.format(Date(choice.currentSavedAt))),
                        ChoiceOption(NEW, choice.otherName, format.format(Date(choice.otherSavedAt))))))
                }, { keep[choice.id] }, select = { keep[choice.id] = it }, cycles = false),
                value = { when (keep[choice.id]) { CURRENT -> choice.currentName; NEW -> choice.otherName; else -> "Choose" } },
                tone = { if (keep[choice.id] == null) ValueTone.EMPTY else ValueTone.CURRENT })
        }
        return object : GroupsPage("Save conflicts", { GameSource.ALL_GAMES }, { listOf(RowGroup("KEEP", rows)) }, {
            listOf(
                FooterButton("cancel", { "Cancel" }) { pages.pop() },
                FooterButton("move", { confirmLabel }, ButtonTone.PRIMARY, { keep.values.all { it != null } },
                    "Choose which save to keep for each game.") {
                    confirmed = true
                    pages.pop()
                    confirm(keep.filterValues { it == NEW }.keys)
                })
        }) {
            override fun onRemoved() {
                super.onRemoved()
                if (!confirmed) cancelled()
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
