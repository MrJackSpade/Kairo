package com.mrjackspade.kairo.frontend

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerBindingsCodecTest {
    private val codec = ControllerBindingsCodec({ emptyList() }, { it in 0..0x7f },
        listOf("up", "down", "button1"), listOf("menu"))

    @Test
    fun keysAndAJoystickInputRoundTrip() {
        val bindings = listOf(ControllerBinding("virtual:up", listOf(0x3a), joystick = "up"),
            ControllerBinding("virtual:a", joystick = "button1"),
            ControllerBinding("virtual:b", listOf(0x20)))
        assertEquals(bindings, codec.parse(codec.toJson(bindings).toString()))
    }

    @Test
    fun onlyKeysMayGoWithAnotherTarget() {
        assertTrue(codec.valid(JSONArray("""[{"input":"virtual:up","keys":[58],"joystick":"up"}]""")))
        assertFalse(codec.valid(JSONArray("""[{"input":"virtual:up","keys":[58],"action":"menu"}]""")))
        assertFalse(codec.valid(JSONArray("""[{"input":"virtual:up","joystick":"up","action":"menu"}]""")))
        assertFalse(codec.valid(JSONArray("""[{"input":"virtual:up","keys":[58],"mouse":"leftButton"}]""")))
        assertFalse(codec.valid(JSONArray("""[{"input":"virtual:up","keys":[58],"joystick":"up","action":"menu"}]""")))
    }
}
