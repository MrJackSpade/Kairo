package com.mrjackspade.kairo.frontend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kairo#183: a value typed beside a choice's options. */
class CustomValueTest {
    private fun mb(value: Int) = UiText.fixed("$value")

    @Test
    fun aWholeNumberInTheRangeAndOnItsStepIsTakenAsTyped() {
        val sizes = CustomValue.range(1, 64, 1, labelOf = ::mb)
        assertNull(sizes.problem("48"))
        assertEquals("48", sizes.valueOf("48"))
        assertNotNull(sizes.problem("65"))
        assertNotNull(sizes.problem("0"))
        assertNotNull(sizes.problem("4.5"))
        assertNotNull(sizes.problem("abc"))
        val stepped = CustomValue.range(8, 64, 8, labelOf = ::mb)
        assertNull(stepped.problem("24"))
        assertNotNull(stepped.problem("20"))
    }

    @Test
    fun aValueTypedInOneUnitIsKeptInAnother() {
        // Memory typed in MB, kept in KB
        val memory = CustomValue.range(1, 64, 1, scale = 1024, labelOf = ::mb)
        assertEquals("49152", memory.valueOf("48"))
        assertEquals("48", memory.typedOf("49152"))
        assertTrue(memory.fits("49152"))
        assertFalse(memory.fits("1000"))
    }

    @Test
    fun aDecimalIsTypedToItsPlacesAndKeptExactly() {
        // A clock typed in MHz to two places, kept in Hz
        val clock = CustomValue.range(100, 10000, 1, scale = 10000, decimals = 2, labelOf = ::mb)
        assertNull(clock.problem("7.16"))
        assertEquals("7160000", clock.valueOf("7.16"))
        assertEquals("7160000", clock.valueOf("7.160".dropLast(1)))
        assertNotNull(clock.problem("7.165"))
        assertNull(clock.problem("8"))
        assertEquals("8000000", clock.valueOf("8"))
        assertEquals("7.16", clock.typedOf("7160000"))
        assertNotNull(clock.problem("0.5"))
    }

    @Test
    fun aWorkflowFieldKeepsATypedValueThatFits() {
        val memory = WorkflowField.Select("memory", UiText.fixed("Memory"),
            { listOf(ChoiceOption("4", UiText.fixed("4")), ChoiceOption("8", UiText.fixed("8"))) }, { "4" },
            custom = { CustomValue.range(1, 64, 1, labelOf = ::mb) })
        val workflow = NewGameWorkflow(listOf(WorkflowPage("m", UiText.fixed("M"), listOf(memory)))) { error("not created") }
        assertEquals("48", workflow.resolve(mapOf("memory" to "48"))["memory"])
        // One that does not fit goes back to the default, as a choice no longer offered does
        assertEquals("4", workflow.resolve(mapOf("memory" to "100"))["memory"])
    }
}
