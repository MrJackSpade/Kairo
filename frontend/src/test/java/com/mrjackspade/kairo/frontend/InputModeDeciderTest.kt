package com.mrjackspade.kairo.frontend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class InputModeDeciderTest {
    private fun input(waits: Long = 0, polls: Long = 0, mouse: Long = 0, enabled: Boolean = false) =
        InputModeDecider.GuestInput(waits, polls, mouse, keyboardWaiting = false, mouseEnabled = enabled)

    /** Feeds [samples] a quarter second apart, as KairoActivity samples, and gives Auto's mode. */
    private fun decide(vararg samples: InputModeDecider.GuestInput): InputModeDecider.Mode {
        val decider = InputModeDecider()
        samples.forEachIndexed { i, sample -> decider.observe(sample, i * 250L) }
        return decider.resolve(InputModeDecider.Mode.AUTO)
    }

    @Test
    fun sustainedMouseReadsChooseTheMouse() {
        assertEquals(InputModeDecider.Mode.MOUSE,
            decide(*Array(6) { input(mouse = it * 10L) }))
    }

    @Test
    fun aMouseTheGuestSetUpChoosesTheMouseWithoutVisibleReads() {
        assertEquals(InputModeDecider.Mode.MOUSE, decide(*Array(6) { input(enabled = true) }))
        assertEquals(InputModeDecider.Mode.KEYBOARD, decide(*Array(6) { input() }))
    }

    @Test
    fun keyboardPollsKeepTheKeyboardOverASetUpMouse() {
        assertEquals(InputModeDecider.Mode.KEYBOARD,
            decide(*Array(6) { input(polls = it * 5L, enabled = true) }))
    }

    @Test
    fun nativeValuesCarryTheSetUpMouse() {
        assertEquals(input(1, 2, 3, enabled = true), InputModeDecider.GuestInput.fromNative(longArrayOf(1, 2, 3, 0, 1)))
        assertThrows(IllegalArgumentException::class.java) {
            InputModeDecider.GuestInput.fromNative(longArrayOf(1, 2, 3, 0))
        }
    }
}
