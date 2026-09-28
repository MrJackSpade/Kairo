package com.mrjackspade.kairo.frontend

import android.app.Activity

/** Runs manual and launch-time catalog checks with the same UI behavior in every Kairo app. */
class CatalogUpdateController(
    private val activity: Activity,
    private val fetch: () -> Boolean,
    private val showStatus: (String) -> Unit,
    private val onChanged: () -> Unit,
    private val onSilentChanged: () -> Unit
) {
    @Volatile private var running = false

    fun check(silent: Boolean) {
        if (running) {
            if (!silent) showStatus("Catalog update is already running")
            return
        }
        running = true
        if (!silent) showStatus("Checking game catalog…")
        Thread {
            var changed = false
            var failure: String? = null
            try { changed = fetch() }
            catch (error: Exception) { failure = error.message ?: "Unknown error" }
            activity.runOnUiThread {
                running = false
                if (!activity.isDestroyed) {
                    if (changed) onChanged()
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
