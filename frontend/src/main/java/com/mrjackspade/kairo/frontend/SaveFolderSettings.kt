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
    /** The save kinds, each with the kind v1.4.0 and earlier gave it (SaveUnit). */
    private val kinds: Map<String, String>,
    private val gameTitle: (SaveUnit) -> String,
    /** A kind's name for the player, such as "Slot 1". */
    private val unitLabel: (String) -> String,
    /** Why the folder cannot change now, such as a running game; null when it can. */
    private val blocked: () -> String?,
    /** Runs on a worker before saves move, such as converting an older save format. */
    private val prepare: ((String) -> Unit) -> Unit = {}
) {
    /** The chosen folder, or null for app storage. */
    val folder: FolderSaveLocation? get() = preferences.getString(PREFERENCE, null)?.let(Uri::parse)?.let { uri ->
        check(FolderGrants.has(activity, uri, write = true)) {
            activity.getString(R.string.kairo_saves_access_lost)
        }
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

    fun row() = SettingsRow.choice("save-folder", activity.getString(R.string.kairo_saves_folder),
        ChoiceSpec(activity.getString(R.string.kairo_saves_folder), { GameSource.ALL_GAMES }, {
            listOf(ChoiceGroup(null, listOf(ChoiceOption(PRIVATE, UiText.res(R.string.kairo_saves_private)),
                ChoiceOption(FOLDER, UiText.res(R.string.kairo_saves_custom_folder)))))
        }, { if (label == null) PRIVATE else FOLDER }, select = { id ->
            if (id == FOLDER) chooseFolder()
            else if (label != null) change(app) { check(preferences.edit().remove(PREFERENCE).commit()) {
                activity.getString(R.string.kairo_saves_remember_failed)
            } }
        }, cycles = false), value = { status ?: label ?: activity.getString(R.string.kairo_saves_private) },
        enabled = { !busy && blocked() == null },
        disabledReason = { if (busy) activity.getString(R.string.kairo_saves_moving_wait) else blocked() })

    /** Returns true when the result belongs to the save folder picker. */
    fun handleActivityResult(code: Int, result: Int, data: Intent?): Boolean {
        if (code != REQUEST) return false
        val uri = data?.data
        if (result != Activity.RESULT_OK || uri == null) return true
        if (uri.toString() == preferences.getString(PREFERENCE, null)) return true
        FolderGrants.take(activity, uri, write = true)
        change(FolderSaveLocation(activity.contentResolver, uri)) {
            check(preferences.edit().putString(PREFERENCE, uri.toString()).commit()) {
                activity.getString(R.string.kairo_saves_remember_failed)
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
        show(activity.getString(R.string.kairo_saves_checking))
        Thread({
            val result = runCatching {
                prepare(::show)
                val migration = SaveMigration(location, target, kinds, activity.cacheDir)
                migration to migration.conflicts()
            }
            activity.runOnUiThread {
                result.fold({ (migration, conflicts) ->
                    if (conflicts.isEmpty()) move(migration, emptySet(), remember)
                    else pages.push(conflictPage(migration, conflicts, target === app, remember))
                }, { error -> finish(activity.getString(R.string.kairo_saves_change_failed, reason(error))) })
            }
        }, "Kairo-save-folder").start()
    }

    private fun move(migration: SaveMigration, keepNew: Set<SaveUnit>, remember: () -> Unit) {
        show(activity.getString(R.string.kairo_saves_moving))
        Thread({
            val result = runCatching {
                migration.run(keepNew, { done, total -> show(activity.getString(R.string.kairo_saves_moving_count, done, total)) }, remember)
            }
            activity.runOnUiThread {
                finish(result.fold({ activity.getString(R.string.kairo_saves_folder_changed) },
                    { error -> activity.getString(R.string.kairo_saves_move_failed, reason(error)) }))
            }
        }, "Kairo-save-folder").start()
    }

    private fun reason(error: Throwable) = error.message ?: activity.getString(R.string.kairo_saves_storage_error)

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
        val newName = activity.getString(if (toPrivate) R.string.kairo_saves_private else R.string.kairo_saves_new_folder)
        var moving = false
        return choicePage(conflicts.map { conflict ->
            Choice(conflict.unit, activity.getString(R.string.kairo_saves_conflict_title, gameTitle(conflict.unit), label(conflict.unit.kind)),
                activity.getString(R.string.kairo_saves_current), conflict.currentSavedAt, newName, conflict.newSavedAt)
        }, activity.getString(R.string.kairo_saves_move), { moving = true; move(migration, it, remember) }) {
            if (!moving) finish(activity.getString(R.string.kairo_saves_folder_unchanged))
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
                Choice(conflict, activity.getString(R.string.kairo_saves_conflict_title, title(conflict.game), label(conflict.unit)),
                    activity.getString(R.string.kairo_saves_earlier_version), conflict.earlierSavedAt,
                    activity.getString(R.string.kairo_saves_this_version), conflict.keyedSavedAt)
            }, activity.getString(R.string.kairo_saves_keep), { keptNewer -> chosen = conflicts.toSet() - keptNewer; latch.countDown() }) {
                latch.countDown()
            })
        }
        latch.await()
        return checkNotNull(chosen) { activity.getString(R.string.kairo_saves_not_moved) }
    }

    /**
     * Asks which copy to keep of each save a game has both from v1.4.0 and at its path: from an
     * earlier version, or from launches by another app before the game opened as the library's
     * (Kairo#56). Blocks the calling worker until the user chooses; returns the conflicts whose
     * earlier copy is kept.
     */
    fun resolvePathConflicts(conflicts: List<GamePathMigration.Conflict>,
                             title: (GameKey) -> String): Set<GamePathMigration.Conflict> {
        val latch = java.util.concurrent.CountDownLatch(1)
        var chosen: Set<GamePathMigration.Conflict>? = null
        activity.runOnUiThread {
            pages.push(choicePage(conflicts.map { conflict ->
                Choice(conflict, activity.getString(R.string.kairo_saves_conflict_title, title(conflict.game), conflict.label),
                    activity.getString(if (conflict.external) R.string.kairo_saves_from_library else R.string.kairo_saves_this_version),
                    conflict.currentSavedAt,
                    activity.getString(if (conflict.external) R.string.kairo_saves_from_other_app else R.string.kairo_saves_earlier_version),
                    conflict.earlierSavedAt)
            }, activity.getString(R.string.kairo_saves_keep), { keptEarlier -> chosen = keptEarlier; latch.countDown() }) {
                latch.countDown()
            })
        }
        latch.await()
        return checkNotNull(chosen) { activity.getString(R.string.kairo_saves_not_moved) }
    }

    /** A kind's name for the player; a v1.4.0 kind is named as the kind it became. */
    private fun label(kind: String) = unitLabel(kinds.entries.firstOrNull { it.value == kind }?.key ?: kind)

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
                value = { when (keep[choice.id]) {
                    CURRENT -> choice.currentName
                    NEW -> choice.otherName
                    else -> activity.getString(R.string.kairo_saves_choose)
                } },
                tone = { if (keep[choice.id] == null) ValueTone.EMPTY else ValueTone.CURRENT })
        }
        return object : GroupsPage(activity.getString(R.string.kairo_saves_conflicts), { GameSource.ALL_GAMES },
            { listOf(RowGroup(activity.getString(R.string.kairo_saves_keep_group), rows)) }, {
            listOf(
                FooterButton("cancel", { activity.getString(R.string.kairo_cancel) }) { pages.pop() },
                FooterButton("move", { confirmLabel }, ButtonTone.PRIMARY, { keep.values.all { it != null } },
                    activity.getString(R.string.kairo_saves_choose_each)) {
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
