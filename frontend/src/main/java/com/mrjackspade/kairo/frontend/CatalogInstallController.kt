package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.content.Intent
import java.util.concurrent.Executors

/** Identical file-picker and installed-catalog actions in both products. */
class CatalogInstallController(private val activity: Activity, private val store: InstalledCatalogs,
                               private val refresh: () -> Unit,
                               private val progress: (CatalogUpdateState?) -> Unit,
                               private val task: (String?) -> Unit = {}) {
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile var busy = false
        private set

    fun catalogs() = store.catalogs()

    fun chooseFile() {
        if (busy) return
        @Suppress("DEPRECATION")
        activity.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, REQUEST)
    }

    fun handleActivityResult(request: Int, result: Int, data: Intent?): Boolean {
        if (request != REQUEST) return false
        if (result == Activity.RESULT_OK) data?.data?.let { uri -> run("Importing catalog…", CatalogUpdateState.IMPORTING) {
            val input = activity.contentResolver.openInputStream(uri) ?: error("Cannot open catalog file")
            val update = CatalogUpdateTask { state -> activity.runOnUiThread {
                if (!activity.isDestroyed) progress(state)
            } }
            "Installed ${store.importFile(input, update)}"
        } }
        return true
    }

    /** Confirmed on the Remove catalog page. */
    fun remove(catalog: InstalledCatalogs.Catalog, removed: () -> Unit = {}) {
        run("Removing catalog…", done = removed) {
            store.remove(catalog.id)
            "Catalog removed"
        }
    }

    private fun run(status: String, initial: CatalogUpdateState = CatalogUpdateState.APPLYING,
                    done: () -> Unit = {}, action: () -> String) {
        if (busy) return
        busy = true
        task(status)
        progress(initial)
        worker.execute {
            val result = runCatching(action)
            val message = result.getOrElse { "Catalog operation failed: ${it.message}" }
            activity.runOnUiThread {
                busy = false
                task(null)
                if (!activity.isDestroyed) {
                    refresh()
                    progress(null)
                    Ui.message(activity, message, long = true)
                    if (result.isSuccess) done()
                }
            }
        }
    }

    companion object { private const val REQUEST = 19606 }
}
