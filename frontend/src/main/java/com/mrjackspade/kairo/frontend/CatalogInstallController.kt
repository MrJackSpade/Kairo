package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import java.util.concurrent.Executors

/** Identical file-picker and installed-catalog actions in both products. */
class CatalogInstallController(private val activity: Activity, private val store: InstalledCatalogs,
                               private val refresh: () -> Unit,
                               private val progress: (CatalogUpdateState?) -> Unit) {
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var busy = false

    fun chooseFile() {
        if (busy) return
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
            val task = CatalogUpdateTask { state -> activity.runOnUiThread {
                if (!activity.isDestroyed) progress(state)
            } }
            "Installed ${store.importFile(input, task)}"
        } }
        return true
    }
    fun remove() {
        if (busy) return
        val catalogs = store.catalogs()
        if (catalogs.isEmpty()) {
            AlertDialog.Builder(activity).setTitle("Remove catalog").setMessage("No installed catalogs")
                .setPositiveButton("Back", null).create().also { it.show(); Ui.styleDialog(it) }
            return
        }
        AlertDialog.Builder(activity).setTitle("Remove catalog")
            .setItems(catalogs.map { it.name }.toTypedArray()) { _, index ->
                val catalog = catalogs[index]
                AlertDialog.Builder(activity).setTitle("Remove ${catalog.name}?")
                    .setMessage("Removes this catalog's metadata and artwork. Your games and personal settings are kept.")
                    .setPositiveButton("Remove") { _, _ -> run("Removing catalog…") {
                        store.remove(catalog.id); "Catalog removed"
                    } }
                    .setNegativeButton("Cancel", null).create().also { it.show(); Ui.styleDialog(it) }
            }.setNegativeButton("Cancel", null).create().also { it.show(); Ui.styleDialog(it) }
    }
    private fun run(status: String, initial: CatalogUpdateState = CatalogUpdateState.APPLYING, action: () -> String) {
        if (busy) return
        busy = true
        Ui.message(activity, status)
        progress(initial)
        worker.execute {
            val result = runCatching(action)
            val message = result.getOrElse { "Catalog operation failed: ${it.message}" }
            activity.runOnUiThread {
                busy = false
                if (!activity.isDestroyed) {
                    refresh()
                    progress(if (result.isSuccess) CatalogUpdateState.UPDATED else CatalogUpdateState.FAILED)
                    Ui.message(activity, message, long = true)
                }
            }
        }
    }
    companion object { private const val REQUEST = 19606 }
}
