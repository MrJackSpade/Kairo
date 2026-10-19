package com.mrjackspade.kairo.frontend

import org.junit.Assert.*
import org.junit.Test

class GuestKeyboardLayoutTest {
    private val page = listOf(GuestKeyboardPage("ABC", listOf(listOf(KeyboardKey("a", 1)))))

    private fun layout(modifiers: Set<Int>, numLock: Int? = null, otherLocks: Set<Int> = emptySet()) =
        GuestKeyboardLayout("TEST", page, modifiers, setOf(10), capsCode = 11, chordShiftCode = 10,
            numLockCode = numLock, otherLockCodes = otherLocks)

    @Test fun everyLockKeyTogglesAndLights() {
        val keyboard = layout(setOf(10, 11, 12, 13), numLock = 12, otherLocks = setOf(13))
        assertEquals(setOf(11, 12, 13), keyboard.lockCodes)
        assertTrue(keyboard.isLock(12))
        assertTrue(keyboard.isLock(13))
        assertFalse(keyboard.isLock(10))
    }

    @Test fun capsAloneIsTheOnlyLockByDefault() {
        assertEquals(setOf(11), layout(setOf(10, 11)).lockCodes)
    }

    @Test(expected = IllegalArgumentException::class)
    fun lockKeysMustBeModifiers() {
        layout(setOf(10, 11), numLock = 12)
    }
}
