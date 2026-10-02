package com.mrjackspade.kairo.frontend

import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Bounded media inspection; results retain discovery order regardless of completion order. */
internal object ParallelScanWork {
    // Archive readers can allocate sizeable buffers and saturate removable storage.
    // Use multiple cores without opening one stream/thread for every library entry.
    val defaultWorkers: Int get() = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)

    fun <T, R : Any> map(items: List<T>, cancelled: AtomicBoolean,
                         workers: Int = defaultWorkers, inspect: (Int, T) -> R): List<R> {
        fun checkCancelled() {
            if (cancelled.get()) throw CancellationException("Cancelled")
        }
        checkCancelled()
        if (items.isEmpty()) return emptyList()
        val count = workers.coerceAtLeast(1).coerceAtMost(items.size)
        val next = AtomicInteger()
        val results = arrayOfNulls<Any>(items.size)
        val executor = Executors.newFixedThreadPool(count) { task ->
            Thread(task, "Kairo-scan-worker")
        }
        val completed = ExecutorCompletionService<Unit>(executor)
        var successful = false
        var interrupted = false
        try {
            repeat(count) {
                completed.submit(java.util.concurrent.Callable {
                    while (true) {
                        checkCancelled()
                        val index = next.getAndIncrement()
                        if (index >= items.size) break
                        results[index] = inspect(index, items[index])
                        checkCancelled()
                    }
                })
            }
            var remaining = count
            while (remaining > 0) {
                checkCancelled()
                val finished = completed.poll(100, TimeUnit.MILLISECONDS) ?: continue
                try { finished.get() }
                catch (error: ExecutionException) { throw error.cause ?: error }
                remaining--
            }
            checkCancelled()
            successful = true
            @Suppress("UNCHECKED_CAST")
            return results.map { it as R }
        } catch (error: InterruptedException) {
            interrupted = true
            throw CancellationException("Interrupted")
        } finally {
            if (!successful) cancelled.set(true)
            executor.shutdownNow()
            // Drain before releasing the pipeline's scan lock: an old scan must
            // never overlap a replacement scan or write/prune its cache afterward.
            while (!executor.isTerminated) {
                try { executor.awaitTermination(100, TimeUnit.MILLISECONDS) }
                catch (_: InterruptedException) { interrupted = true; cancelled.set(true) }
            }
            if (interrupted) Thread.currentThread().interrupt()
        }
    }
}
