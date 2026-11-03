package com.mrjackspade.kairo.frontend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Options whose choices and default come from other options' values (Kairo#143). */
class MachineOptionDependsTest {
    private val cpus = mapOf("xt" to listOf("8088"), "at" to listOf("286", "386"))

    private val cpu = MachineOption("cpu", UiText.fixed("CPU"), { emptyList() }, "", dependsOn = listOf("machine"),
        choicesWith = { values -> cpus.getValue(values.getValue("machine")).map { ChoiceOption(it, it) } },
        defaultWith = { values -> cpus.getValue(values.getValue("machine")).first() })

    @Test
    fun choicesFollowTheOptionsItDependsOn() {
        assertEquals(listOf("8088"), cpu.choices(mapOf("machine" to "xt")).map { it.id })
        assertEquals(listOf("286", "386"), cpu.choices(mapOf("machine" to "at")).map { it.id })
    }

    @Test
    fun defaultFollowsThem() {
        assertEquals("8088", cpu.default(mapOf("machine" to "xt")))
        assertEquals("286", cpu.default(mapOf("machine" to "at")))
    }

    @Test
    fun aValueTheOthersNoLongerAllowDoesNotFit() {
        assertTrue(cpu.fits("386", mapOf("machine" to "at")))
        assertFalse(cpu.fits("386", mapOf("machine" to "xt")))
    }

    @Test
    fun anOptionWithoutDependenciesKeepsItsChoices() {
        val clock = MachineOption("clock", UiText.fixed("Clock"), listOf(ChoiceOption("local", "Local"), ChoiceOption("utc", "UTC")), "local")
        assertEquals(listOf("local", "utc"), clock.choices(mapOf("machine" to "xt")).map { it.id })
        assertEquals("local", clock.default(emptyMap()))
        assertTrue(clock.fits("utc", emptyMap()))
    }
}
