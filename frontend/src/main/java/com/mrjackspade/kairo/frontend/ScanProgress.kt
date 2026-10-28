package com.mrjackspade.kairo.frontend

import java.util.concurrent.atomic.AtomicInteger

/**
 * The status a library scan shows for each file: "Hashing N of M" for a file the library has not
 * seen, where M counts only the files this scan hashes, and "Confirming N of M" for one it knows.
 * A large file's hash also shows how far through it the read is. Workers report from several
 * threads; each worker's current file is its own.
 */
class ScanProgress(
    /** Files this scan identifies by hashing them. */
    val toHash: Int,
    /** Files the library knows, which keep their identity. */
    val toConfirm: Int,
    private val report: (UiText) -> Unit
) {
    private val hashing = AtomicInteger()
    private val confirming = AtomicInteger()
    /** A worker's file: [count] is its number among the files hashed, when it was hashed from the start. */
    private class Current(val label: UiText, val count: Int? = null, var percent: Int = -1)
    private val current = ThreadLocal<Current?>()

    /** A worker starts on [path]: one the scan hashes when [hash], or else one it confirms. */
    fun start(path: String, hash: Boolean) {
        val state = if (hash) hashing.incrementAndGet().let { Current(counted(it, path), it) }
            else Current(UiText.res(R.string.kairo_scan_confirming, confirming.incrementAndGet(), toConfirm, path))
        current.set(state)
        report(state.label)
    }

    /**
     * The current file is hashed after all: [detail] names what is read, such as an archive's
     * member, or a file a confirmation found new.
     */
    fun hashing(detail: String) {
        val count = current.get()?.count
        val state = if (count != null) Current(counted(count, detail), count)
            else Current(UiText.res(R.string.kairo_scan_hashing_file, detail))
        current.set(state)
        report(state.label)
    }

    private fun counted(count: Int, detail: String) = UiText.res(R.string.kairo_scan_hashing, count, toHash, detail)

    /** [read] of [total] bytes of the current hash are done; a large file reports each percent. */
    fun read(read: Long, total: Long) {
        if (total < LARGE_FILE) return
        val state = current.get() ?: return
        val percent = (read * 100 / total).toInt().coerceIn(0, 100)
        if (percent == state.percent) return
        state.percent = percent
        report(UiText.res(R.string.kairo_scan_percent, state.label, percent))
    }

    companion object {
        /** Files below this size hash too quickly for a percentage to be read. */
        const val LARGE_FILE = 16L * 1024 * 1024
    }
}
