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

    @Test
    fun onlyKeysMayGoWithAnotherTarget() {
        assertTrue(codec.valid(JSONArray("""[{"input":"virtual:up","keys":[58],"joystick":"up"}]""")))
        assertFalse(codec.valid(JSONArray("""[{"input":"virtual:up","keys":[58],"action":"menu"}]""")))
        assertFalse(codec.valid(JSONArray("""[{"input":"virtual:up","joystick":"up","action":"menu"}]""")))
        assertFalse(codec.valid(JSONArray("""[{"input":"virtual:up","keys":[58],"mouse":"leftButton"}]""")))
        assertFalse(codec.valid(JSONArray("""[{"input":"virtual:up","keys":[58],"joystick":"up","action":"menu"}]""")))
    }
}
