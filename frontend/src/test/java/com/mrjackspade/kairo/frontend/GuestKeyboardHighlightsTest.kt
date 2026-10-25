package com.mrjackspade.kairo.frontend

import org.junit.Assert.assertEquals
import org.junit.Test

class GuestKeyboardHighlightsTest {
    private val shift = 1
    private val ctrl = 2
    private val down = 10
    private val five = 11
    private val shifts = setOf(shift)
    private val modifiers = setOf(shift, ctrl)

    private fun lit(vararg holds: List<Int>): Set<Int> {
        val router = InputRouter({ _, _ -> })
        holds.forEachIndexed { index, keys -> router.hold("h$index", keys) }
        return GuestKeyboardHighlights.litKeys(router.pressedKeys(), router.holds(), shifts, modifiers)
    }

    @Test
    fun aChordsShiftDoesNotLight() = assertEquals(setOf(down), lit(listOf(shift, down)))

    @Test
    fun shiftHeldAloneLights() = assertEquals(setOf(shift), lit(listOf(shift)))

    @Test
    fun shiftHeldAsShiftBesideAChordLights() = assertEquals(setOf(shift, down, five), lit(listOf(shift, down), listOf(shift), listOf(five)))

    @Test
    fun shiftWithOnlyModifiersLights() = assertEquals(setOf(shift, ctrl), lit(listOf(shift, ctrl)))
}
