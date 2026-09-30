package com.mrjackspade.kairo.frontend

import org.json.JSONArray
import org.json.JSONObject

/** The same stored physical and virtual controller profiles for every Kairo core. */
class ControllerBindingsCodec(
    private val defaults: () -> List<ControllerBinding>,
    private val validKey: (Int) -> Boolean,
    private val joystickControls: Collection<String>,
    private val actions: Collection<String>
) {
    private val input = Regex("(?:virtual:[a-z0-9]+|button:[0-9]{1,4}|(?:axis|hat):[0-9]{1,3}:[+-])")
    private val cycleInputs = setOf("virtual:l1", "virtual:r1", "virtual:l2", "virtual:r2")

    fun valid(array: JSONArray): Boolean {
        if (array.length() > 128) return false
        val inputs = HashSet<String>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: return false
            val source = item.optString("input")
            if (!input.matches(source) || !inputs.add(source)) return false
            if (source.startsWith("virtual:") &&
                source.removePrefix("virtual:") !in PhysicalControllerBindings.controls) return false
            val keys = item.optJSONArray("keys")
            val action = item.optString("action").takeIf(String::isNotEmpty)
            val joystick = item.optString("joystick").takeIf(String::isNotEmpty)
            val mouse = item.optString("mouse").takeIf(String::isNotEmpty)
            val cycleKeys = item.optJSONArray("cycleKeys")
            if (listOf(keys != null, action != null, joystick != null, mouse != null,
                    cycleKeys != null)
                    .count { it } != 1) return false
            if (keys != null) {
                if (keys.length() !in 1..4) return false
                val mapped = ArrayList<Int>()
                for (keyIndex in 0 until keys.length()) {
                    val value = keys.opt(keyIndex)
                    if (value !is Int && value !is Long) return false
                    mapped.add((value as Number).toInt())
                }
                if (mapped.any { !validKey(it) } || mapped.distinct().size != mapped.size) return false
            }
            if (action != null && action !in actions) return false
            if (joystick != null && joystick !in joystickControls) return false
            if (mouse != null && mouse !in MouseInputRouter.TARGETS) return false
            if (item.has("mouseSpeed")) {
                val speed = item.opt("mouseSpeed") as? Number ?: return false
                val value = speed.toDouble()
                if (mouse?.startsWith("move") != true || !value.isFinite() ||
                    value.toFloat() !in MouseInputRouter.MIN_SPEED..MouseInputRouter.MAX_SPEED)
                    return false
            }
            if (cycleKeys != null) {
                if (source !in cycleInputs || cycleKeys.length() !in 2..16) return false
                val mapped = ArrayList<Int>()
                for (keyIndex in 0 until cycleKeys.length()) {
                    val value = cycleKeys.opt(keyIndex)
                    if (value !is Int && value !is Long) return false
                    mapped.add((value as Number).toInt())
                }
                if (mapped.any { !validKey(it) } || mapped.distinct().size != mapped.size)
                    return false
            }
        }
        return true
    }

    fun parse(text: String?): List<ControllerBinding> = try {
        if (text == null) defaults() else {
            val array = JSONArray(text)
            if (!valid(array)) defaults() else (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                val keys = item.optJSONArray("keys")
                val cycleKeys = item.optJSONArray("cycleKeys")
                ControllerBinding(item.getString("input"),
                    if (keys == null) emptyList() else (0 until keys.length()).map(keys::getInt),
                    item.optString("action").takeIf(String::isNotEmpty),
                    item.optString("joystick").takeIf(String::isNotEmpty),
                    item.optString("mouse").takeIf(String::isNotEmpty),
                    if (cycleKeys == null) emptyList() else
                        (0 until cycleKeys.length()).map(cycleKeys::getInt),
                    item.optDouble("mouseSpeed", 1.0).toFloat())
            }
        }
    } catch (_: Exception) { defaults() }

    fun toJson(bindings: List<ControllerBinding>): JSONArray = JSONArray().also { array ->
        bindings.forEach { binding ->
            val item = JSONObject().put("input", binding.input)
            when {
                binding.action != null -> item.put("action", binding.action)
                binding.joystick != null -> item.put("joystick", binding.joystick)
                binding.mouse != null -> item.put("mouse", binding.mouse)
                binding.cycleKeys.isNotEmpty() -> item.put("cycleKeys", JSONArray(binding.cycleKeys))
                else -> item.put("keys", JSONArray(binding.keys))
            }
            if (binding.mouseSpeed != 1f) item.put("mouseSpeed", binding.mouseSpeed.toDouble())
            array.put(item)
        }
        require(valid(array)) { "Invalid controller mapping" }
    }
}
