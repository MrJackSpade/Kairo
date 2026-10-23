package com.mrjackspade.kairo.uitest

/**
 * Assertions that record themselves: each one logs what it checked, what it found and whether it
 * passed, then fails the test with the same words when it did not.
 */
class Expect(private val log: TestLog) {
    fun that(what: String, holds: Boolean, found: () -> String = { "" }) {
        val detail = found()
        log.step(if (holds) "pass" else "fail", what, if (detail.isEmpty()) emptyMap() else mapOf("found" to detail))
        if (!holds) throw AssertionError(if (detail.isEmpty()) what else "$what; found $detail")
    }

    fun <T> equal(what: String, expected: T, actual: T) =
        that("$what is $expected", expected == actual) { actual.toString() }

    fun atLeast(what: String, minimum: Double, actual: Double) =
        that("$what is at least $minimum", actual >= minimum) { actual.toString() }
}
