package com.mrjackspade.kairo.frontend

import android.view.KeyEvent
import android.view.MotionEvent
import org.json.JSONArray
import org.json.JSONObject

data class PhysicalControllerBinding(val input: String, val control: String)

/** Maps Android controller events to a stable, device-independent control layout. */
object PhysicalControllerBindings {
    val controls = listOf("up", "down", "left", "right", "a", "b", "x", "y",
        "l1", "r1", "l2", "r2", "start", "select", "menu",
        "lsup", "lsdown", "lsleft", "lsright",
        "rsup", "rsdown", "rsleft", "rsright")
    private val inputPattern = Regex("(?:button:[0-9]{1,4}|(?:axis|hat):[0-9]{1,3}:[+-])")

    fun valid(array: JSONArray): Boolean {
        if (array.length() > 128) return false
        val inputs = HashSet<String>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: return false
            val input = item.optString("input")
            if (!inputPattern.matches(input) || !inputs.add(input) ||
                item.optString("control") !in controls) return false
        }
        return true
    }

    fun parse(text: String?): List<PhysicalControllerBinding> = try {
        if (text == null) defaults()
        else {
            val array = JSONArray(text)
            if (!valid(array)) defaults()
            else (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                PhysicalControllerBinding(item.getString("input"), item.getString("control"))
            }
        }
    } catch (_: Exception) { defaults() }

    fun toJson(bindings: List<PhysicalControllerBinding>): JSONArray = JSONArray().also { array ->
        bindings.forEach { array.put(JSONObject().put("input", it.input).put("control", it.control)) }
        require(valid(array)) { "Invalid physical controller mapping" }
    }

    fun defaults() = listOf(
        PhysicalControllerBinding("button:${KeyEvent.KEYCODE_BUTTON_A}", "a"),
        PhysicalControllerBinding("button:${KeyEvent.KEYCODE_BUTTON_B}", "b"),
        PhysicalControllerBinding("button:${KeyEvent.KEYCODE_BUTTON_X}", "x"),
        PhysicalControllerBinding("button:${KeyEvent.KEYCODE_BUTTON_Y}", "y"),
        PhysicalControllerBinding("button:${KeyEvent.KEYCODE_BUTTON_L1}", "l1"),
        PhysicalControllerBinding("button:${KeyEvent.KEYCODE_BUTTON_R1}", "r1"),
        PhysicalControllerBinding("button:${KeyEvent.KEYCODE_BUTTON_L2}", "l2"),
        PhysicalControllerBinding("button:${KeyEvent.KEYCODE_BUTTON_R2}", "r2"),
        PhysicalControllerBinding("button:${KeyEvent.KEYCODE_BUTTON_START}", "start"),
        PhysicalControllerBinding("button:${KeyEvent.KEYCODE_BUTTON_SELECT}", "select"),
        PhysicalControllerBinding("button:${KeyEvent.KEYCODE_BUTTON_MODE}", "menu"),
        PhysicalControllerBinding("button:${KeyEvent.KEYCODE_DPAD_UP}", "up"),
        PhysicalControllerBinding("button:${KeyEvent.KEYCODE_DPAD_DOWN}", "down"),
        PhysicalControllerBinding("button:${KeyEvent.KEYCODE_DPAD_LEFT}", "left"),
        PhysicalControllerBinding("button:${KeyEvent.KEYCODE_DPAD_RIGHT}", "right"),
        PhysicalControllerBinding("hat:${MotionEvent.AXIS_HAT_X}:-", "left"),
        PhysicalControllerBinding("hat:${MotionEvent.AXIS_HAT_X}:+", "right"),
        PhysicalControllerBinding("hat:${MotionEvent.AXIS_HAT_Y}:-", "up"),
        PhysicalControllerBinding("hat:${MotionEvent.AXIS_HAT_Y}:+", "down"),
        PhysicalControllerBinding("axis:${MotionEvent.AXIS_X}:-", "lsleft"),
        PhysicalControllerBinding("axis:${MotionEvent.AXIS_X}:+", "lsright"),
        PhysicalControllerBinding("axis:${MotionEvent.AXIS_Y}:-", "lsup"),
        PhysicalControllerBinding("axis:${MotionEvent.AXIS_Y}:+", "lsdown"),
        PhysicalControllerBinding("axis:${MotionEvent.AXIS_Z}:-", "rsleft"),
        PhysicalControllerBinding("axis:${MotionEvent.AXIS_Z}:+", "rsright"),
        PhysicalControllerBinding("axis:${MotionEvent.AXIS_RZ}:-", "rsup"),
        PhysicalControllerBinding("axis:${MotionEvent.AXIS_RZ}:+", "rsdown"),
        PhysicalControllerBinding("axis:${MotionEvent.AXIS_LTRIGGER}:+", "l2"),
        PhysicalControllerBinding("axis:${MotionEvent.AXIS_RTRIGGER}:+", "r2")
    )
}
