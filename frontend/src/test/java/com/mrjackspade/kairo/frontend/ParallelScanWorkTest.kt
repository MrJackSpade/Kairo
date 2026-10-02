package com.mrjackspade.kairo.frontend

import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class ParallelScanWorkTest {
    @Test fun overlapsWorkButRetainsInputOrderAndBoundsConcurrency() {
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        val firstWave = CountDownLatch(4)
        val results = ParallelScanWork.map((0..31).toList(), AtomicBoolean(), 4) { index, item ->
            val running = active.incrementAndGet()
            maximum.updateAndGet { maxOf(it, running) }
            try {
                if (index < 4) {
                    firstWave.countDown()
                    check(firstWave.await(5, TimeUnit.SECONDS)) { "Work did not run concurrently" }
                }
                item * 2
            } finally { active.decrementAndGet() }
        }
        assertEquals((0..31).map { it * 2 }, results)
        assertEquals(4, maximum.get())
        assertEquals(0, active.get())
    }

    @Test fun cancellationDrainsWorkersBeforeReturningAndDoesNotScheduleTheRest() {
        val cancelled = AtomicBoolean()
        val started = CountDownLatch(2)
        val active = AtomicInteger()
        val inspected = AtomicInteger()
        val caller = Executors.newSingleThreadExecutor()
        try {
            val scan = caller.submit<Boolean> {
                try {
                    ParallelScanWork.map((0..99).toList(), cancelled, 2) { _, item ->
                        inspected.incrementAndGet()
                        active.incrementAndGet()
                        started.countDown()
                        try {
                            while (!cancelled.get()) Thread.sleep(5)
                            item
                        } finally { active.decrementAndGet() }
                    }
                    false
                } catch (_: CancellationException) { true }
            }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            cancelled.set(true)
            assertTrue(scan.get(5, TimeUnit.SECONDS))
            assertEquals(0, active.get())
            assertEquals(2, inspected.get())
        } finally { caller.shutdownNow() }
    }

    @Test fun fatalFailurePropagatesAndDrainsOtherWorkers() {
        val cancelled = AtomicBoolean()
        val started = CountDownLatch(2)
        val active = AtomicInteger()
        val failure = IllegalStateException("fixture")
        try {
            ParallelScanWork.map((0..9).toList(), cancelled, 2) { index, item ->
                active.incrementAndGet()
                try {
                    started.countDown()
                    check(started.await(5, TimeUnit.SECONDS))
                    if (index == 0) throw failure
                    while (!cancelled.get()) Thread.sleep(5)
                    item
                } finally { active.decrementAndGet() }
            }
            fail("Expected failure")
        } catch (actual: IllegalStateException) { assertSame(failure, actual) }
        assertTrue(cancelled.get())
        assertEquals(0, active.get())
    }

    @Test fun emptyAndAlreadyCancelledScansDoNotInspectAnything() {
        assertEquals(emptyList<Int>(), ParallelScanWork.map(emptyList<Int>(), AtomicBoolean()) { _, _ ->
            error("Unexpected inspection")
        })
        try {
            ParallelScanWork.map(listOf(1), AtomicBoolean(true)) { _, _ -> error("Unexpected inspection") }
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
    }
}
