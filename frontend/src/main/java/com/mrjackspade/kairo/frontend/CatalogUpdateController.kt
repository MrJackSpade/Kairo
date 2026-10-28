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
            if (!silent) showStatus(activity.getString(R.string.kairo_catalogs_already_running))
            return
        }
        lateinit var current: CatalogUpdateTask
        current = CatalogUpdateTask { state -> activity.runOnUiThread {
            if (task === current && !activity.isDestroyed) showProgress(state)
        } }
        task = current
        showProgress(CatalogUpdateState.CHECKING)
        if (!silent) showStatus(activity.getString(R.string.kairo_catalogs_checking))
        worker = Thread {
            var changed = false
            var failure: String? = null
            try { changed = fetch(current) }
            catch (error: Exception) { failure = error.message ?: activity.getString(R.string.kairo_unknown_error) }
            // A failed core feed must not prevent an installed catalog from updating.
            // Preserve partial successes so their metadata is refreshed in the UI.
            try {
                current.ensureActive()
                if (fetchInstalled?.invoke(current) == true) changed = true
            } catch (error: Exception) {
                failure = listOfNotNull(failure, error.message ?: activity.getString(R.string.kairo_unknown_error)).joinToString("; ")
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
                        failure != null -> activity.getString(if (changed) R.string.kairo_catalogs_update_partial
                            else R.string.kairo_catalogs_update_failed, failure)
                        changed -> activity.getString(R.string.kairo_catalogs_updated)
                        else -> activity.getString(R.string.kairo_catalogs_already_current)
                    })
                    else if (changed) onSilentChanged()
                }
            }
        }.apply { name = "Kairo-catalog-update"; start() }
    }
}
