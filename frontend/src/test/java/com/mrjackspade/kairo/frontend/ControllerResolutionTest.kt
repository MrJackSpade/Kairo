package com.mrjackspade.kairo.frontend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ControllerResolutionTest {
    private val thumbL = "button:106"
    private val physical = listOf(PhysicalControllerBinding(thumbL, "l3"), PhysicalControllerBinding("button:96", "a"))
    private val system = listOf(ControllerBinding("virtual:l3", listOf(1)), ControllerBinding("virtual:a", listOf(2)))
    // A KairoDos game's stick press, bound to the physical input as its catalog has it (Kairo#141).
    private val game = ControllerBinding(thumbL, listOf(119))

    @Test
    fun aPhysicalInputDoesWhatItsControlIsBoundTo() {
        assertEquals(system[0], ControllerResolution.forInput(thumbL, physical, system))
        assertEquals(system[1], ControllerResolution.forInput("button:96", physical, system))
    }

    @Test
    fun aDirectBindingComesBeforeTheControl() {
        assertEquals(game, ControllerResolution.forInput(thumbL, physical, system + game))
    }

    @Test
    fun anOnScreenControlDoesWhatItsPhysicalButtonDoes() {
        assertEquals(system[0], ControllerResolution.forControl("l3", physical, system))
        assertEquals(game, ControllerResolution.forControl("l3", physical, system + game))
        assertEquals(system[1], ControllerResolution.forControl("a", physical, system + game))
    }

    @Test
    fun anUnmappedInputOrUnboundControlDoesNothing() {
        assertNull(ControllerResolution.forInput("button:99", physical, system))
        assertNull(ControllerResolution.forControl("r3", physical, system))
    }
}
