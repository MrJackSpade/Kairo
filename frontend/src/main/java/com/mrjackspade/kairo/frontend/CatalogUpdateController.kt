package com.mrjackspade.kairo.frontend

import android.app.Activity

/** Runs manual and launch-time catalog checks with the same UI behavior in every Kairo app. */
class CatalogUpdateController(
    private val activity: Activity,
    private val fetch: (CatalogUpdateTask) -> Boolean,
    private val showStatus: (String) -> Unit,
    private val onChanged: () -> Unit,
    private val onSilentChanged: () -> Unit,
    private val showProgress: (CatalogUpdateState?) -> Unit,
    private val fetchInstalled: ((CatalogUpdateTask) -> Boolean)? = null
) {
    private var task: CatalogUpdateTask? = null
    private var worker: Thread? = null

    fun cancel() {
        task?.cancel()
        task = null
        worker?.interrupt()
        worker = null
        showProgress(null)
    }

    fun check(silent: Boolean) {
        if (task != null) {
            if (!silent) showStatus("Catalog update is already running")
            return
        }
        lateinit var current: CatalogUpdateTask
        current = CatalogUpdateTask { state -> activity.runOnUiThread {
            if (task === current && !activity.isDestroyed) showProgress(state)
        } }
        task = current
        showProgress(CatalogUpdateState.CHECKING)
        if (!silent) showStatus("Checking catalogs…")
        worker = Thread {
            var changed = false
            var failure: String? = null
            try { changed = fetch(current) }
            catch (error: Exception) { failure = error.message ?: "Unknown error" }
            // A failed core feed must not prevent an installed catalog from updating.
            // Preserve partial successes so their metadata is refreshed in the UI.
            try {
                current.ensureActive()
                if (fetchInstalled?.invoke(current) == true) changed = true
            } catch (error: Exception) {
                failure = listOfNotNull(failure, error.message ?: "Unknown error").joinToString("; ")
            }
            activity.runOnUiThread {
                if (task !== current) return@runOnUiThread
                task = null
                worker = null
                if (!activity.isDestroyed) {
                    if (changed) onChanged()
                    showProgress(when {
                        failure != null -> CatalogUpdateState.FAILED
                        changed -> CatalogUpdateState.UPDATED
                        else -> CatalogUpdateState.CURRENT
                    })
                    if (!silent) showStatus(when {
                        failure != null -> "Catalog update ${if (changed) "partially completed" else "failed"}: $failure"
                        changed -> "Catalogs updated"
                        else -> "Catalogs are already current"
                    })
                    else if (changed) onSilentChanged()
                }
            }
        }.apply { name = "Kairo-catalog-update"; start() }
    }
}
