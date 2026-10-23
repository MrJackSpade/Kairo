package com.mrjackspade.kairo.uitest

import androidx.test.platform.app.InstrumentationRegistry

/**
 * How waits end. A wait polls its condition until it holds; one that has not seen it after 30
 * seconds (the bound the user set, 2026-10-22) fails the test with what it waited for. The
 * instrumentation argument `patienceSeconds` changes the bound for a run. Every wait reports what it
 * is still waiting for every 10 seconds.
 */
object Patience {
    /** The bound in milliseconds. */
    val bound: Long = InstrumentationRegistry.getArguments().getString("patienceSeconds")?.let { value ->
        requireNotNull(value.toLongOrNull()) { "patienceSeconds must be a whole number of seconds, not \"$value\"" } * 1000
    } ?: DEFAULT_MILLIS

    private const val DEFAULT_MILLIS = 30_000L

    private const val POLL_MILLIS = 50L
    private const val REPORT_MILLIS = 10_000L

    /** Polls [condition] until it holds; [what] names it for the log and the failure. */
    fun until(log: TestLog, what: String, condition: () -> Boolean) {
        val start = System.currentTimeMillis()
        var reported = start
        while (!condition()) {
            val now = System.currentTimeMillis()
            if (now - start > bound) throw AssertionError("Still waiting after ${bound / 1000} s for $what")
            if (now - reported >= REPORT_MILLIS) {
                log.step("waiting", "${(now - start) / 1000} s for $what")
                reported = now
            }
            Thread.sleep(POLL_MILLIS)
        }
    }
}
