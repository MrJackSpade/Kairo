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

    /** A JIS layout's pages, as the FM Towns and the X68000 build them (Shift is 0x70 here). */
    private val jis = GuestKeyboardLayout("JIS", listOf(
        GuestKeyboardPage("ABC", JisMainBlock.alphabetRows(0x71, SHIFT, listOf(KeyboardKey("Space", JisMainBlock.SPACE)))),
        GuestKeyboardPage("?123", JisMainBlock.symbolRows())), setOf(SHIFT, 0x71), setOf(SHIFT), null, SHIFT)

    @Test fun aSymbolIsTheKeyThatTypesItOnTheGuestsLayout() {
        // US Shift+; is :, which JIS types on its own : key without Shift.
        assertEquals(listOf(JisMainBlock.COLON), jis.symbolKeys(':', emptySet()))
        // US Shift+8 is *, JIS Shift+:.
        assertEquals(listOf(SHIFT, JisMainBlock.COLON), jis.symbolKeys('*', emptySet()))
        // JIS + is Shift+;, @ its own key, " Shift+2.
        assertEquals(listOf(SHIFT, JisMainBlock.SEMICOLON), jis.symbolKeys('+', emptySet()))
        assertEquals(listOf(JisMainBlock.AT), jis.symbolKeys('@', emptySet()))
        assertEquals(listOf(SHIFT, JisMainBlock.digit('2')), jis.symbolKeys('"', emptySet()))
    }

    @Test fun lettersDigitsSpaceAndUnknownSymbolsKeepTheirPlace() {
        assertNull(jis.symbolKeys('a', emptySet()))
        assertNull(jis.symbolKeys('7', emptySet()))
        assertNull(jis.symbolKeys(' ', emptySet()))
        assertNull(jis.symbolKeys('€', emptySet()))
    }

    private companion object {
        const val SHIFT = 0x70
    }
}
