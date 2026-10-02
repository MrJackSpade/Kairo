package com.mrjackspade.kairo.frontend

import android.app.Activity

/** Runs manual and launch-time catalog checks with the same UI behavior in every Kairo app. */
class CatalogUpdateController(
    private val activity: Activity,
    private val fetch: (CatalogUpdateTask) -> Boolean,
    private val showStatus: (String) -> Unit,
    private val onChanged: () -> Unit,
    private val onSilentChanged: () -> Unit,
    private val showProgress: (CatalogUpdateState?) -> Unit
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
        if (!silent) showStatus("Checking game catalog…")
        worker = Thread {
            var changed = false
            var failure: String? = null
            try { changed = fetch(current) }
            catch (error: Exception) { failure = error.message ?: "Unknown error" }
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
                        failure != null -> "Catalog update failed: $failure"
                        changed -> "Game catalog updated"
                        else -> "Game catalog is already current"
                    })
                    else if (changed) onSilentChanged()
                }
            }
        }.apply { name = "Kairo-catalog-update"; start() }
    }
}
