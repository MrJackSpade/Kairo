package com.mrjackspade.kairo.frontend

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.view.InputDevice
import android.view.MotionEvent
import androidx.annotation.StringRes

/** [detected] names the layout within a sentence, as in "Detected: with sticks". */
enum class ControllerLayout(@StringRes val label: Int, @StringRes val detected: Int, val key: String) {
    WITH_STICKS(R.string.kairo_layout_with_sticks, R.string.kairo_layout_detected_with_sticks, "withSticks"),
    WITHOUT_STICKS(R.string.kairo_layout_without_sticks, R.string.kairo_layout_detected_without_sticks, "withoutSticks")
}

/** Capability policy is separate from Android enumeration so firmware edge cases are testable. */
object ControllerCapabilities {
    data class Device(val name: String, val descriptor: String, val external: Boolean,
                      val leftStick: Boolean, val rightStick: Boolean)
    data class Detection(val device: Device?) {
        val layout = if (device?.leftStick == true && device.rightStick)
            ControllerLayout.WITH_STICKS else ControllerLayout.WITHOUT_STICKS
    }

    fun detect(devices: List<Device>, preferredDescriptor: String? = null) = Detection(devices.sortedWith(
        compareBy<Device> { it.external }.thenBy { it.descriptor != preferredDescriptor }
            .thenBy { it.descriptor }).firstOrNull())

    fun connected(): List<Device> = InputDevice.getDeviceIds().toList().mapNotNull(InputDevice::getDevice)
        .filter { !it.isVirtual && (it.supportsSource(InputDevice.SOURCE_GAMEPAD) ||
            it.supportsSource(InputDevice.SOURCE_JOYSTICK) ||
            (it.supportsSource(InputDevice.SOURCE_DPAD) &&
                it.hasKeys(android.view.KeyEvent.KEYCODE_BUTTON_A, android.view.KeyEvent.KEYCODE_BUTTON_B).any { key -> key })) }
        .map { device ->
            fun centered(axis: Int): Boolean = device.getMotionRange(axis, InputDevice.SOURCE_JOYSTICK)
                ?.let { it.min < 0f && it.max > 0f } == true
            Device(device.name, device.descriptor,
                Build.VERSION.SDK_INT >= 29 && device.isExternal,
                centered(MotionEvent.AXIS_X) && centered(MotionEvent.AXIS_Y),
                (centered(MotionEvent.AXIS_Z) && centered(MotionEvent.AXIS_RZ)) ||
                    (centered(MotionEvent.AXIS_RX) && centered(MotionEvent.AXIS_RY)))
        }
}

/** A global choice, independent of per-game/custom bindings. Auto stays fixed during a session. */
class ControllerConfiguration(private val preferences: SharedPreferences,
                              private val detect: () -> ControllerCapabilities.Detection = {
                                  ControllerCapabilities.detect(ControllerCapabilities.connected(),
                                      preferences.getString("controller_auto_device_v1", null))
                              }) {
    enum class Choice(@StringRes val label: Int) {
        AUTO(R.string.kairo_auto), WITH_STICKS(R.string.kairo_layout_with_sticks), WITHOUT_STICKS(R.string.kairo_layout_without_sticks)
    }
    private var automatic = detect().also(::rememberDevice).layout
    private var session = false
    val configured get() = preferences.contains("controller_configuration_v1")
    val needsSetup get() = !configured || (choice == Choice.AUTO && detect().device == null)
    val choice: Choice get() = runCatching {
        Choice.valueOf(preferences.getString("controller_configuration_v1", null) ?: "AUTO")
    }.getOrDefault(Choice.AUTO)
    val layout: ControllerLayout get() = when (choice) {
        Choice.AUTO -> automatic
        Choice.WITH_STICKS -> ControllerLayout.WITH_STICKS
        Choice.WITHOUT_STICKS -> ControllerLayout.WITHOUT_STICKS
    }
    fun label(context: Context): String = if (choice == Choice.AUTO)
        context.getString(R.string.kairo_layout_auto_detected, context.getString(layout.detected))
        else context.getString(choice.label)

    fun select(value: Choice) {
        preferences.edit().putString("controller_configuration_v1", value.name).apply()
        automatic = detect().also(::rememberDevice).layout
    }
    private fun rememberDevice(detection: ControllerCapabilities.Detection) {
        detection.device?.let {
            preferences.edit().putString("controller_auto_device_v1", it.descriptor).apply()
        }
    }
    fun devicesChanged() { if (!session) automatic = detect().also(::rememberDevice).layout }
    fun beginSession() { devicesChanged(); session = true }
    fun endSession() { session = false; devicesChanged() }

    /**
     * The required setup page on a cold start with no saved choice, or with Auto saved and no
     * controller detected. Nothing is preselected without a controller, and Continue waits for
     * a choice. Back closes the app.
     */
    fun setupPage(context: Context, product: String, exit: () -> Unit, onSelected: () -> Unit): SetupPage {
        val detection = detect()
        val choices = if (detection.device == null) listOf(Choice.WITH_STICKS, Choice.WITHOUT_STICKS)
            else Choice.entries
        var selected: Choice? = if (detection.device == null) null else Choice.AUTO
        lateinit var page: SetupPage
        page = SetupPage(product, null, context.getString(R.string.kairo_controller_layout),
            if (detection.device == null) context.getString(R.string.kairo_layout_setup_no_controller) else null,
            content = {
                listOf(RowGroup(null, choices.map { option ->
                    SettingsRow.option("layout:${option.name}", context.getString(option.label),
                        if (option == Choice.AUTO) context.getString(R.string.kairo_controller_layout_detected,
                            context.getString(detection.layout.detected)) else null,
                        { option == selected }) {
                        selected = option
                        page.refresh()
                    }
                }, context.getString(R.string.kairo_layout_setup_foot)))
            },
            footerButtons = {
                listOf(FooterButton("continue", { context.getString(R.string.kairo_continue) }, ButtonTone.PRIMARY, { selected != null },
                    context.getString(R.string.kairo_layout_setup_choose)) {
                    val choice = selected ?: return@FooterButton
                    if (choice == Choice.AUTO && detect().device == null) {
                        selected = null
                        page.refresh()
                        return@FooterButton
                    }
                    select(choice)
                    onSelected()
                })
            }, onBack = exit, startUnfocused = detection.device == null)
        return page
    }
}

/** Missing variants fall back; an explicitly empty list is an intentional mapping. */
object ControllerDefaults {
    /**
     * Mapping layers merged input by input, lowest first: the system's defaults (its catalog), the
     * player's global layer, the game's catalog layer, the player's game layer. A layer's binding
     * replaces the input's; an explicit null (ControllerBinding.unbound) removes it. What we ship
     * holds no explicit null, so only a player's own choice leaves an input unbound.
     */
    fun cascade(vararg layers: List<ControllerBinding>): List<ControllerBinding> {
        val merged = LinkedHashMap<String, ControllerBinding>()
        for (layer in layers) for (binding in layer) {
            if (binding.unbound) merged.remove(binding.input) else merged[binding.input] = binding
        }
        return merged.values.toList()
    }

    /**
     * The layer a player saves: over [inherited] (the layers below it), it gives [edited]. Bindings
     * that differ from what they inherit, and an explicit null for each inherited input [edited]
     * leaves unbound; inputs they did not change keep following the layers below.
     */
    fun layerOver(inherited: List<ControllerBinding>, edited: List<ControllerBinding>): List<ControllerBinding> {
        val below = inherited.associateBy { it.input }
        val bound = edited.filterNot { it.unbound }
        val inputs = bound.map { it.input }.toSet()
        return bound.filter { below[it.input] != it } + below.keys.filter { it !in inputs }.map(::ControllerBinding)
    }

    /**
     * A mapping the player saved before mappings were layers: the whole mapping, as it played. As a
     * layer it binds what it bound and removes everything else, so it still plays the same. Its
     * left stick did what its D-pad did, and Menu opened the menu, where it left them unbound; a
     * D-pad key cycle stays on the D-pad (a stick cannot hold one).
     */
    fun legacyLayer(saved: List<ControllerBinding>): List<ControllerBinding> {
        val bound = saved.filterNot { it.unbound }.associateBy { it.input }.toMutableMap()
        for (direction in listOf("up", "down", "left", "right")) {
            val dpad = bound["virtual:$direction"]?.takeIf { it.cycleKeys.isEmpty() } ?: continue
            bound.putIfAbsent("virtual:ls$direction", dpad.copy(input = "virtual:ls$direction"))
        }
        bound.putIfAbsent("virtual:menu", ControllerBinding("virtual:menu", action = "menu"))
        return bound.values.toList() + PhysicalControllerBindings.controls.map { "virtual:$it" }
            .filter { it !in bound }.map(::ControllerBinding)
    }

    data class Resolution<T>(val bindings: List<T>, val fallback: Boolean)
    fun <T> resolve(layout: ControllerLayout, withoutSticks: List<T>, withSticks: List<T>?): Resolution<T> =
        if (layout == ControllerLayout.WITH_STICKS && withSticks != null) Resolution(withSticks, false)
        else Resolution(withoutSticks, layout == ControllerLayout.WITH_STICKS)
}
