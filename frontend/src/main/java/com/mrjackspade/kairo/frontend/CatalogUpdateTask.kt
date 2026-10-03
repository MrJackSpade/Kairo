package com.mrjackspade.kairo.frontend

import java.util.concurrent.CancellationException

enum class CatalogUpdateState(val label: String, val busy: Boolean) {
    CHECKING("Checking catalogs…", true),
    DOWNLOADING("Downloading catalogs…", true),
    APPLYING("Applying catalogs…", true),
    CURRENT("Catalogs are current", false),
    UPDATED("Catalogs updated", false),
    FAILED("Catalog check failed", false)
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
