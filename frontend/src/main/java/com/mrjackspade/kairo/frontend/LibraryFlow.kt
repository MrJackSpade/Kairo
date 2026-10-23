package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** The library workflow shared by every Kairo emulator. Media discovery stays in the backend. */
class LibraryFlow<T : LibraryItem>(
    private val activity: Activity,
    private val preferences: SharedPreferences,
    val screen: LibraryScreen<T>,
    private val requestCode: Int,
    private val cached: (Uri) -> List<T>,
    private val scan: (Uri, ScanDepth, AtomicBoolean, (String) -> Unit) -> List<T>,
    private val emptyStatus: String,
    private val expiredStatus: String,
    private val scanSummary: (List<T>) -> String,
    private val onScanComplete: (List<T>) -> Unit = {},
    private val onFolderSelected: ((Uri) -> Unit)? = null,
    private val onFolderError: (String) -> Unit = {},
    private val writable: Boolean = false,
    /** Saves an edited list as the folder's cached library; required by [edit]. */
    private val store: ((Uri, List<T>) -> Unit)? = null
) {
    var tree: Uri? = null
    var entries: List<T> = emptyList()
    private var displayedEntries: List<T>? = null
    private var scanCancelled = AtomicBoolean(false)
    /** Edits made while a scan runs. That scan may have listed the folder before them. */
    private val scanEdits = ArrayList<(List<T>) -> List<T>>()
    /** Edited lists are saved in order, after any scan's own save that preceded them. */
    private val storeWorker by lazy { Executors.newSingleThreadExecutor { Thread(it, "Kairo-library-store") } }
    /** The cached library is being read off the main thread; see [whenLoaded]. */
    private var loading = false
    private var loadGeneration = 0
    /** Edits made before the cached list arrived; they apply to it when it does. */
    private val loadEdits = ArrayList<(List<T>) -> List<T>>()
    private val afterLoad = ArrayList<() -> Unit>()
    /** A scan or a full recheck is running. */
    var scanning = false
        private set
    var rehashing = false
        private set

    init { screen.needsFolder = { tree?.let(::hasGrant) != true } }

    /** The folder's name, shown as the library flyout title and the folder row value. */
    private fun folderLabel(uri: Uri): String = try {
        DocumentsContract.getTreeDocumentId(uri).substringAfterLast('/').substringAfterLast(':')
            .ifEmpty { screen.strings.folder }
    } catch (_: IllegalArgumentException) {
        // Only a folder chosen through the document tree picker has a tree document ID.
        screen.strings.folder
    }

    fun hasGrant(uri: Uri): Boolean = FolderGrants.has(activity, uri, writable)

    fun hasWriteGrant(uri: Uri): Boolean = FolderGrants.has(activity, uri, write = true)

    fun restore(): Uri? {
        val saved = preferences.getString("rom_tree", null)?.let(Uri::parse)
        tree = saved
        screen.showFolder(saved?.let(::folderLabel))
        when {
            saved == null -> screen.showStatus(emptyStatus)
            !hasGrant(saved) -> screen.showStatus(expiredStatus)
            else -> {
                // A large cache takes too long to read on the main thread at every launch.
                loading = true
                val generation = ++loadGeneration
                screen.showStatus("Loading library\u2026")
                Thread {
                    val result = runCatching { cached(saved) }
                    activity.runOnUiThread { finishLoad(generation, saved, result) }
                }.apply { name = "Kairo-library-load"; start() }
            }
        }
        return saved
    }

    /** Runs [action] once the cached library is available, as for a frontend launch at startup. */
    fun whenLoaded(action: () -> Unit) {
        if (loading) afterLoad += action else action()
    }

    private fun finishLoad(generation: Int, folder: Uri, result: Result<List<T>>) {
        if (generation != loadGeneration || activity.isDestroyed) return
        loading = false
        result.onSuccess { list ->
            val edited = loadEdits.fold(list) { current, change -> change(current) }
            if (loadEdits.isNotEmpty()) save(folder, edited)
            entries = edited
            screen.showEntries(edited)
            displayedEntries = edited
            if (!scanning) screen.showStatus(scanSummary(edited))
        }.onFailure { failure ->
            // The cache only saves rescanning; the startup scan rebuilds one that cannot be read.
            screen.showStatus("Library cache unreadable: ${failure.message ?: failure.javaClass.simpleName}")
        }
        loadEdits.clear()
        runAfterLoad()
    }

    /** A newer list replaced the cached one before it arrived. */
    private fun supersedeLoad() {
        if (!loading) return
        loadGeneration++
        loading = false
        loadEdits.clear()
    }

    private fun runAfterLoad() {
        val actions = afterLoad.toList()
        afterLoad.clear()
        actions.forEach { it() }
    }

    fun show() {
        screen.showFolder(tree?.let(::folderLabel))
        // restore() and scan completion have already presented this list.
        // Repeating showEntries reparses catalog records for every game.
        if (displayedEntries !== entries) {
            screen.showEntries(entries)
            displayedEntries = entries
        } else {
            screen.refreshPin()
            screen.reannounceSelection()
        }
        screen.showStatus(when {
            tree == null -> emptyStatus
            !hasGrant(tree!!) -> expiredStatus
            else -> scanSummary(entries)
        })
    }

    fun chooseFolder() {
        try {
            @Suppress("DEPRECATION")
            activity.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }, requestCode)
        } catch (error: Exception) {
            val message = error.message ?: "Could not open folder chooser"
            screen.showStatus(message)
            onFolderError(message)
        }
    }

    /** Returns true when the result belongs to this library. */
    fun handleActivityResult(code: Int, result: Int, data: Intent?): Boolean {
        if (code != requestCode) return false
        val selected = data?.data
        if (result != Activity.RESULT_OK || selected == null) {
            if (tree == null) screen.showStatus(emptyStatus)
            return true
        }
        try {
            val keepWrite = writable ||
                (data.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
            FolderGrants.take(activity, selected, keepWrite)
            scanCancelled.set(true)
            supersedeLoad()
            tree = selected
            entries = emptyList()
            preferences.edit().putString("rom_tree", selected.toString()).apply()
            screen.showFolder(folderLabel(selected))
            screen.showEntries(entries)
            displayedEntries = entries
            runAfterLoad()
            onFolderSelected?.invoke(selected) ?: refresh(ScanDepth.EVERY_FOLDER)
        } catch (failure: Exception) {
            val message = "Cannot keep folder access: ${failure.message}"
            screen.showStatus(message)
            onFolderError(message)
        }
        return true
    }

    /** [announce] puts the scan summary in the message bar, for scans the user started. */
    fun refresh(depth: ScanDepth, announce: Boolean = false) {
        val selected = tree ?: run { screen.showStatus(emptyStatus); return }
        if (!hasGrant(selected)) { screen.showStatus(expiredStatus); return }
        scanCancelled.set(true)
        val cancelled = AtomicBoolean(false)
        scanCancelled = cancelled
        // This scan lists the folder after every earlier edit was made.
        scanEdits.clear()
        scanning = true
        rehashing = depth.rehash
        screen.showStatus(if (depth.rehash) "Rehashing folder…" else "Checking the games folder…")
        fun finished() {
            if (scanCancelled !== cancelled) return
            scanning = false
            rehashing = false
        }
        // Scan workers report from several threads, once per game: only the newest message waits for
        // the main thread. One screen update per game (7,669 in a DOS library) held the check up for
        // seconds (Kairo#32).
        val pendingStatus = java.util.concurrent.atomic.AtomicReference<String?>()
        Thread {
            try {
                val scanned = scan(selected, depth, cancelled) { message ->
                    if (pendingStatus.getAndSet(message) == null) activity.runOnUiThread {
                        val latest = pendingStatus.getAndSet(null)
                        if (latest != null && !cancelled.get() && tree == selected) screen.showStatus(latest)
                    }
                }
                activity.runOnUiThread {
                    finished()
                    if (!cancelled.get() && tree == selected && !activity.isDestroyed) {
                        // Scan results are newer than the cache, even one still loading.
                        supersedeLoad()
                        val found = if (scanEdits.isEmpty()) scanned else
                            scanEdits.fold(scanned) { list, change -> change(list) }.also { save(selected, it) }
                        scanEdits.clear()
                        entries = found
                        screen.showEntries(found)
                        displayedEntries = found
                        runAfterLoad()
                        onScanComplete(found)
                        val summary = scanSummary(found)
                        screen.showStatus(summary)
                        if (announce) Ui.message(activity, summary)
                    }
                }
            } catch (_: CancellationException) {
                activity.runOnUiThread { finished() }
            } catch (failure: Exception) {
                activity.runOnUiThread {
                    finished()
                    if (!cancelled.get() && tree == selected && !activity.isDestroyed)
                        screen.showStatus("Scan failed: ${failure.message ?: "Unknown error"}")
                }
            }
        }.apply { name = "Kairo-library-scan"; start() }
    }

    /**
     * Applies a change this app made to the folder, such as an added or removed archive, without
     * rescanning it. [change] must be idempotent: it is applied again to a scan already running.
     */
    fun edit(change: (List<T>) -> List<T>) {
        val selected = tree ?: error("No library folder")
        checkNotNull(store) { "This library cannot be edited in place" }
        if (scanning) scanEdits += change
        if (loading) {
            // The cached list has not arrived; the change applies to it when it does.
            loadEdits += change
            return
        }
        val edited = change(entries)
        save(selected, edited)
        entries = edited
        screen.showEntries(edited)
        displayedEntries = edited
        if (!scanning) screen.showStatus(scanSummary(edited))
    }

    /** The document [entry] came from was removed: drop every entry read from it. */
    fun removeSource(entry: T) = edit { list -> list.filter { it.uri != entry.uri } }

    private fun save(folder: Uri, list: List<T>) {
        val write = store!!
        storeWorker.execute {
            try {
                write(folder, list)
            } catch (failure: Exception) {
                activity.runOnUiThread {
                    val message = "Could not save library: ${failure.message ?: failure.javaClass.simpleName}"
                    screen.showStatus(message)
                    Ui.message(activity, message, long = true)
                }
            }
        }
    }

    fun cancel() {
        scanCancelled.set(true)
        scanning = false
        rehashing = false
    }
}
