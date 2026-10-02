package com.mrjackspade.kairo.frontend

import java.util.concurrent.CancellationException

enum class CatalogUpdateState(val label: String, val busy: Boolean) {
    CHECKING("Checking game catalog…", true),
    DOWNLOADING("Downloading game catalog…", true),
    APPLYING("Applying game catalog…", true),
    CURRENT("Game catalog is current", false),
    UPDATED("Game catalog updated", false),
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
