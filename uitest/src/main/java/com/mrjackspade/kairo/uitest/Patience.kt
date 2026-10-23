package com.mrjackspade.kairo.uitest

import androidx.test.platform.app.InstrumentationRegistry

/**
 * How waits end. A wait polls its condition until it holds. With the instrumentation argument
 * `patienceSeconds`, a wait that has not seen its condition after that long fails the test; without
 * it, waits have no bound. Every wait reports what it is still waiting for every 10 seconds, so a
 * stuck wait is visible in the log as it happens.
 */
object Patience {
    /** The bound in milliseconds, or null for none. */
    val bound: Long? = InstrumentationRegistry.getArguments().getString("patienceSeconds")?.let { value ->
        requireNotNull(value.toLongOrNull()) { "patienceSeconds must be a whole number of seconds, not \"$value\"" } * 1000
    }

    private const val POLL_MILLIS = 50L
    private const val REPORT_MILLIS = 10_000L

    /** Polls [condition] until it holds; [what] names it for the log and the failure. */
    fun until(log: TestLog, what: String, condition: () -> Boolean) {
        val start = System.currentTimeMillis()
        var reported = start
        while (!condition()) {
            val now = System.currentTimeMillis()
            bound?.let { limit ->
                if (now - start > limit) throw AssertionError("Still waiting after ${limit / 1000} s for $what")
            }
            if (now - reported >= REPORT_MILLIS) {
                log.step("waiting", "${(now - start) / 1000} s for $what")
                reported = now
            }
            Thread.sleep(POLL_MILLIS)
        }
    }
}
