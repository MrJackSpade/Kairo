package com.mrjackspade.kairo.frontend

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerBindingsCodecTest {
    private val codec = ControllerBindingsCodec({ it in 0..0x7f },
        listOf("up", "down", "button1"), listOf("menu"))

    @Test
    fun anExplicitNullRoundTripsAndStandsAlone() {
        val bindings = listOf(ControllerBinding("virtual:up"), ControllerBinding("virtual:b", listOf(0x20)))
        val json = codec.toJson(bindings)
        assertEquals(true, json.getJSONObject(0).getBoolean("unbound"))
        assertEquals(bindings, codec.parse(json.toString()))
        assertFalse(codec.valid(JSONArray("[{\"input\":\"virtual:up\",\"unbound\":true,\"keys\":[32]}]")))
        assertFalse(codec.valid(JSONArray("[{\"input\":\"virtual:up\",\"unbound\":false}]")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun dataThatIsNotAMappingFailsWhereItIsRead() {
        codec.parse("[{\"input\":\"virtual:up\"}]")
    }

    @Test
    fun keysAndAJoystickInputRoundTrip() {
        val bindings = listOf(ControllerBinding("virtual:up", listOf(0x3a), joystick = "up"),
            ControllerBinding("virtual:a", joystick = "button1"),
            ControllerBinding("virtual:b", listOf(0x20)))
        assertEquals(bindings, codec.parse(codec.toJson(bindings).toString()))
    }

    /** Kairo#173: keys go with a joystick input, a mouse target or a key cycle; an action stands alone. */
    @Test
    fun keysMayGoWithAnyTargetButAnAction() {
        assertTrue(codec.valid(JSONArray("""[{"input":"virtual:up","keys":[58],"joystick":"up"}]""")))
        assertTrue(codec.valid(JSONArray("""[{"input":"virtual:up","keys":[58],"mouse":"moveUp"}]""")))
        assertTrue(codec.valid(JSONArray("""[{"input":"virtual:left","keys":[58],"cycleKeys":[49,50]}]""")))
        assertFalse(codec.valid(JSONArray("""[{"input":"virtual:up","keys":[58],"action":"menu"}]""")))
        assertFalse(codec.valid(JSONArray("""[{"input":"virtual:up","joystick":"up","action":"menu"}]""")))
        assertFalse(codec.valid(JSONArray("""[{"input":"virtual:up","joystick":"up","mouse":"moveUp"}]""")))
        assertFalse(codec.valid(JSONArray("""[{"input":"virtual:up","keys":[58],"joystick":"up","action":"menu"}]""")))
    }

    @Test
    fun keysWithAMouseTargetOrAKeyCycleRoundTrip() {
        val bindings = listOf(ControllerBinding("virtual:up", listOf(0x3a), mouse = "moveUp", mouseSpeed = 2f),
            ControllerBinding("virtual:left", listOf(0x3b), cycleKeys = listOf(0x31, 0x32)),
            ControllerBinding("virtual:right", listOf(0x3c, 0x3d, 0x3e, 0x3f, 0x40), joystick = "up"))
        assertEquals(bindings, codec.parse(codec.toJson(bindings).toString()))
    }
}
