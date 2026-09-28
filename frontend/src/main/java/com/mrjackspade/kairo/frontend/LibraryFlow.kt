package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/** The library workflow shared by every Kairo emulator. Media discovery stays in the backend. */
class LibraryFlow<T : LibraryItem>(
    private val activity: Activity,
    private val preferences: SharedPreferences,
    val screen: LibraryScreen<T>,
    private val requestCode: Int,
    private val cached: (Uri) -> List<T>,
    private val scan: (Uri, Boolean, AtomicBoolean, (String) -> Unit) -> List<T>,
    private val folderLabel: (Uri) -> String,
    private val emptyStatus: String,
    private val expiredStatus: String,
    private val scanSummary: (List<T>) -> String,
    private val onScanComplete: (List<T>) -> Unit = {},
    private val onFolderSelected: ((Uri) -> Unit)? = null,
    private val onFolderError: (String) -> Unit = {}
) {
    var tree: Uri? = null
    var entries: List<T> = emptyList()
    private var scanCancelled = AtomicBoolean(false)

    fun hasGrant(uri: Uri): Boolean = activity.contentResolver.persistedUriPermissions.any {
        it.uri == uri && it.isReadPermission
    }

    fun restore(): Uri? {
        val saved = preferences.getString("rom_tree", null)?.let(Uri::parse)
        tree = saved
        screen.showFolder(saved?.let(folderLabel))
        when {
            saved == null -> screen.showStatus(emptyStatus)
            !hasGrant(saved) -> screen.showStatus(expiredStatus)
            else -> {
                entries = cached(saved)
                screen.showEntries(entries)
                screen.showStatus(scanSummary(entries))
            }
        }
        return saved
    }

    fun show() {
        screen.showFolder(tree?.let(folderLabel))
        screen.showEntries(entries)
        screen.showStatus(when {
            tree == null -> emptyStatus
            !hasGrant(tree!!) -> expiredStatus
            else -> scanSummary(entries)
        })
    }

    fun chooseFolder() {
        @Suppress("DEPRECATION")
        activity.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }, requestCode)
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
            activity.contentResolver.takePersistableUriPermission(selected,
                Intent.FLAG_GRANT_READ_URI_PERMISSION)
            scanCancelled.set(true)
            tree = selected
            entries = emptyList()
            preferences.edit().putString("rom_tree", selected.toString()).apply()
            screen.showFolder(folderLabel(selected))
            screen.showEntries(entries)
            onFolderSelected?.invoke(selected) ?: refresh(false)
        } catch (failure: Exception) {
            val message = "Cannot keep folder access: ${failure.message}"
            screen.showStatus(message)
            onFolderError(message)
        }
        return true
    }

    fun refresh(forceHash: Boolean) {
        val selected = tree ?: run { screen.showStatus(emptyStatus); return }
        if (!hasGrant(selected)) { screen.showStatus(expiredStatus); return }
        scanCancelled.set(true)
        val cancelled = AtomicBoolean(false)
        scanCancelled = cancelled
        screen.showStatus(if (forceHash) "Rehashing folder…" else "Scanning folder…")
        Thread {
            try {
                val found = scan(selected, forceHash, cancelled) { message ->
                    activity.runOnUiThread {
                        if (!cancelled.get() && tree == selected) screen.showStatus(message)
                    }
                }
                activity.runOnUiThread {
                    if (!cancelled.get() && tree == selected && !activity.isDestroyed) {
                        entries = found
                        screen.showEntries(found)
                        onScanComplete(found)
                        screen.showStatus(scanSummary(found))
                    }
                }
            } catch (_: CancellationException) {
            } catch (failure: Exception) {
                activity.runOnUiThread {
                    if (!cancelled.get() && tree == selected && !activity.isDestroyed)
                        screen.showStatus("Scan failed: ${failure.message ?: "Unknown error"}")
                }
            }
        }.apply { name = "Kairo-library-scan"; start() }
    }

    fun cancel() { scanCancelled.set(true) }
}
