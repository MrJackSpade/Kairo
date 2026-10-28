package com.mrjackspade.kairo.frontend

import androidx.annotation.StringRes
import java.util.concurrent.CancellationException

enum class CatalogUpdateState(@StringRes val label: Int, val busy: Boolean) {
    CHECKING(R.string.kairo_catalogs_checking, true),
    DOWNLOADING(R.string.kairo_catalogs_downloading, true),
    IMPORTING(R.string.kairo_catalogs_importing, true),
    APPLYING(R.string.kairo_catalogs_applying, true),
    CURRENT(R.string.kairo_catalogs_current, false),
    UPDATED(R.string.kairo_catalogs_updated, false),
    FAILED(R.string.kairo_catalogs_check_failed, false)
}

/** Per-check cancellation and progress, shared by the UI and snapshot downloader. */
class CatalogUpdateTask(private val progress: (CatalogUpdateState) -> Unit = {}) {
    @Volatile private var cancelled = false
    fun cancel() { cancelled = true }
    fun ensureActive() {
        if (cancelled || Thread.currentThread().isInterrupted) throw CancellationException("Catalog check cancelled")
    }
    fun report(state: CatalogUpdateState) { ensureActive(); progress(state) }
}
