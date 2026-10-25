package com.mrjackspade.kairo.frontend

import org.junit.Assert.assertEquals
import org.junit.Test

class SymbolPageTest {
    @Test
    fun theJisSymbolPageHasOneReturnBesideSpace() {
        val rows = JisMainBlock.symbolRows()
        assertEquals(1, rows.flatten().count { it.code == JisMainBlock.RETURN })
        val bottom = rows.last()
        assertEquals(bottom.indexOfFirst { it.code == JisMainBlock.SPACE } + 1, bottom.indexOfFirst { it.code == JisMainBlock.RETURN })
    }

    @Test
    fun aMachineWithoutEscOrTabStartsItsRowsWithDigitsAndSpace() {
        val rows = SymbolPage.rows(listOf(KeyboardKey("1", 1)), KeyboardKey("Del", 2), listOf(KeyboardKey("!", 1, chordShift = true)),
            listOf(KeyboardKey("-", 3)), listOf(KeyboardKey("=", 3, chordShift = true)),
            KeyboardKey("Space", 4, 5f), KeyboardKey("Return", 5, 1.5f))
        assertEquals(listOf("1", "Del"), rows[0].map { it.label })
        assertEquals(listOf("!"), rows[1].map { it.label })
        assertEquals(listOf("Space", "Return"), rows[4].map { it.label })
    }
}
