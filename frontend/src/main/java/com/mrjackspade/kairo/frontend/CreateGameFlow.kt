package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.OutputStream

/** A blank disk a player makes a new game on, in a format and size the app's system used. */
sealed class BlankDisk(val id: String, val label: UiText) {
    /**
     * An image written to the games folder at once: [extension] is its file extension; [write]
     * writes the whole blank image, as the system's own tools would find a new, unformatted disk.
     */
    class Image(id: String, label: UiText, val extension: String, val write: (OutputStream) -> Unit) : BlankDisk(id, label)

    /**
     * An empty drive the new game starts on at once ([start], with its name). The app writes the
     * game to the games folder once it holds a file; an empty one leaves nothing behind.
     */
    class Drive(id: String, label: UiText, val start: (name: String) -> Unit) : BlankDisk(id, label)
}

/**
 * Manage library › New game: a row for each blank disk the app makes. The player names the game;
 * an image is written to a folder of that name in the games folder, `<name>/<name>.<extension>`,
 * and the library finds it there as a game of its own, set up as any game the catalog does not
 * know: its drives (installer or system disks from anywhere, through Disks), its startup, its
 * settings. An empty drive starts at once ([BlankDisk.Drive]).
 */
object CreateGameFlow {
    /** The longest name a new game's folder and disk take. */
    private const val MAX_NAME = 128

    fun <T : LibraryItem> rows(activity: Activity, library: LibraryFlow<T>, blanks: List<BlankDisk>,
                               folder: String): List<SettingsRow> = blanks.map { blank ->
        SettingsRow.action("create-${blank.id}", blank.label.resolve(activity),
            enabled = { library.tree != null },
            disabledReason = { activity.getString(R.string.kairo_library_choose_in_manage) },
            tray = {
                if (library.tree?.let(library::hasWriteGrant) != true) TraySpec(activity.getString(R.string.kairo_create_grant, folder),
                    listOf(TrayButton(activity.getString(R.string.kairo_cancel)),
                        TrayButton(activity.getString(R.string.kairo_choose_named, folder), ButtonTone.PRIMARY) { library.chooseFolder() }),
                    focus = 0)
                else TraySpec(input = TrayInput(activity.getString(R.string.kairo_create_name_hint),
                    activity.getString(R.string.kairo_create_name_default)), buttons = listOf(
                    TrayButton(activity.getString(R.string.kairo_cancel)),
                    TrayButton(activity.getString(R.string.kairo_create), ButtonTone.PRIMARY) { name ->
                        create(activity, library, blank, name.trim(), folder)
                    }))
            })
    }

    private fun <T : LibraryItem> create(activity: Activity, library: LibraryFlow<T>, blank: BlankDisk, name: String,
                                         folder: String) {
        val screen = library.screen
        if (!usable(name)) {
            screen.showStatus(activity.getString(R.string.kairo_create_bad_name))
            return
        }
        if (blank is BlankDisk.Drive) {
            blank.start(name)
            return
        }
        val tree = library.tree ?: return
        screen.showStatus(activity.getString(R.string.kairo_creating, name), LibraryScreen.StatusKind.TASK)
        Thread {
            val result = runCatching { write(activity.contentResolver, tree, blank as BlankDisk.Image, name, folder, activity) }
            activity.runOnUiThread {
                result.onSuccess {
                    screen.showStatus(activity.getString(R.string.kairo_created, name), LibraryScreen.StatusKind.SUMMARY)
                    library.refresh(ScanDepth.EVERY_FOLDER, announce = true)
                }.onFailure { failure ->
                    screen.showStatus(failure.message ?: activity.getString(R.string.kairo_create_failed, name))
                }
            }
        }.apply { this.name = "create-game"; start() }
    }

    /** A name a folder and file can take on every storage provider. */
    fun usable(name: String) = name.isNotEmpty() && name.length <= MAX_NAME && name != "." && name != ".." &&
        name.none { it in "/\\:*?\"<>|" || it < ' ' }

    /** Writes `<name>/<name>.<extension>` under [tree]'s root; fails when [name] is already there. */
    private fun write(resolver: ContentResolver, tree: Uri, blank: BlankDisk.Image, name: String, folder: String, activity: Activity) {
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        check(name !in childNames(resolver, tree, root)) { activity.getString(R.string.kairo_create_exists, name, folder) }
        val directory = DocumentsContract.createDocument(resolver, root, Document.MIME_TYPE_DIR, name)
            ?: error(activity.getString(R.string.kairo_create_failed, name))
        val file = DocumentsContract.createDocument(resolver, directory, "application/octet-stream", "$name.${blank.extension}")
            ?: error(activity.getString(R.string.kairo_create_failed, name))
        checkNotNull(resolver.openOutputStream(file, "w")) { activity.getString(R.string.kairo_create_failed, name) }
            .use { blank.write(it) }
    }

    /** The names in [tree]'s root folder, for a product's own new game ([BlankDisk.Drive]). */
    fun rootNames(resolver: ContentResolver, tree: Uri): Set<String> =
        childNames(resolver, tree, DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)))

    private fun childNames(resolver: ContentResolver, tree: Uri, parent: Uri): Set<String> {
        val query = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(parent))
        return checkNotNull(resolver.query(query, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)) {
            "Could not read the games folder"
        }.use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }
    }
}
