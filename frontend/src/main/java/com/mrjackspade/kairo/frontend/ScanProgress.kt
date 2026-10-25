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
    private val report: (String) -> Unit
) {
    private val hashing = AtomicInteger()
    private val confirming = AtomicInteger()
    private class Current(var label: String, var percent: Int = -1)
    private val current = ThreadLocal<Current?>()

    /** A worker starts on [path]: one the scan hashes when [hash], or else one it confirms. */
    fun start(path: String, hash: Boolean) {
        val label = if (hash) "Hashing ${hashing.incrementAndGet()} of $toHash: $path"
            else "Confirming ${confirming.incrementAndGet()} of $toConfirm: $path"
        current.set(Current(label))
        report(label)
    }

    /**
     * The current file is hashed after all: [detail] names what is read, such as an archive's
     * member, or a file a confirmation found new.
     */
    fun hashing(detail: String) {
        val state = current.get()
        val label = if (state != null && state.label.startsWith("Hashing ")) "${state.label.substringBefore(": ")}: $detail"
            else "Hashing $detail"
        current.set(Current(label))
        report(label)
    }

    /** [read] of [total] bytes of the current hash are done; a large file reports each percent. */
    fun read(read: Long, total: Long) {
        if (total < LARGE_FILE) return
        val state = current.get() ?: return
        val percent = (read * 100 / total).toInt().coerceIn(0, 100)
        if (percent == state.percent) return
        state.percent = percent
        report("${state.label} $percent%")
    }

    companion object {
        /** Files below this size hash too quickly for a percentage to be read. */
        const val LARGE_FILE = 16L * 1024 * 1024
    }
}
