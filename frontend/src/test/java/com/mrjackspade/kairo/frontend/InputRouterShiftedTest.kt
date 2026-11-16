package com.mrjackspade.kairo.frontend

import com.mrjackspade.kairo.frontend.InputRouter.Companion.SHIFT_FRAMES
import com.mrjackspade.kairo.frontend.InputRouter.Companion.SYMBOL_HOLD_FRAMES
import org.junit.Assert.assertEquals
import org.junit.Test

/** A Shift chord's Shift reaches the guest before its key, and nothing is let go before it is pressed (Kairo#191). */
class InputRouterShiftedTest {
    private val shift = 1
    private val star = 20
    private val one = 21
    private val shifts = setOf(shift)

    /** Each key change sent: the key, down or up, and the guest frames it waits for. */
    private val sent = mutableListOf<Triple<Int, Boolean, Int>>()
    private val router = InputRouter({ code, down -> sent += Triple(code, down, 0) },
        { code, down, frames -> sent += Triple(code, down, frames) }, {})

    @Test
    fun aChordsShiftGoesDownFirstAndItsKeyAfterFrames() {
        router.holdShifted("touch-key:$star", listOf(shift, star), shifts)
        assertEquals(listOf(Triple(shift, true, 0), Triple(star, true, SHIFT_FRAMES)), sent)
    }

    @Test
    fun aChordLetsGoOnceItsKeyHasBeenDown() {
        router.holdShifted("touch-key:$star", listOf(shift, star), shifts)
        sent.clear()
        router.releaseShifted("touch-key:$star")
        val frames = SHIFT_FRAMES + SYMBOL_HOLD_FRAMES
        assertEquals(listOf(Triple(star, false, frames), Triple(shift, false, frames)), sent)
    }

    @Test
    fun aKeyWithoutShiftGoesAtOnce() {
        router.holdShifted("touch-key:$one", listOf(one), shifts)
        router.releaseShifted("touch-key:$one")
        assertEquals(listOf(Triple(one, true, 0), Triple(one, false, 0)), sent)
    }

    @Test
    fun aReleaseNeverReachesTheGuestBeforeItsPress() {
        router.hold("late", listOf(one), 3)
        router.release("late")
        assertEquals(listOf(Triple(one, true, 3), Triple(one, false, 3)), sent)
    }

    @Test
    fun releasingAllGoesAtOnceAfterDroppingWhatWasQueued() {
        router.hold("late", listOf(one), 3)
        sent.clear()
        router.releaseAll()
        assertEquals(listOf(Triple(one, false, 0)), sent)
    }

    @Test
    fun aChordIsOneHoldSoItsShiftDoesNotLight() {
        router.holdShifted("touch-key:$star", listOf(shift, star), shifts)
        assertEquals(listOf(listOf(shift, star)), router.holds())
        assertEquals(setOf(star), GuestKeyboardHighlights.litKeys(router.pressedKeys(), router.holds(), shifts, shifts))
    }
}
