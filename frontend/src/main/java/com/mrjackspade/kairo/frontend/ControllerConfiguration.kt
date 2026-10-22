package com.mrjackspade.kairo.frontend

import android.content.SharedPreferences
import android.os.Build
import android.view.InputDevice
import android.view.MotionEvent

enum class ControllerLayout(val label: String, val key: String) {
    WITH_STICKS("With sticks", "withSticks"),
    WITHOUT_STICKS("Without sticks", "withoutSticks")
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
    enum class Choice(val label: String) { AUTO("Auto"), WITH_STICKS("With sticks"), WITHOUT_STICKS("Without sticks") }
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
    val label: String get() = if (choice == Choice.AUTO) "Auto (${layout.label.lowercase()})" else choice.label

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
    fun setupPage(product: String, exit: () -> Unit, onSelected: () -> Unit): SetupPage {
        val detection = detect()
        val choices = if (detection.device == null) listOf(Choice.WITH_STICKS, Choice.WITHOUT_STICKS)
            else Choice.entries
        var selected: Choice? = if (detection.device == null) null else Choice.AUTO
        lateinit var page: SetupPage
        page = SetupPage(product, null, "Controller layout",
            if (detection.device == null) "No controller detected. Choose a layout to continue." else null,
            content = {
                listOf(RowGroup(null, choices.map { option ->
                    SettingsRow.option("layout:${option.name}", option.label,
                        if (option == Choice.AUTO) "Detected: ${detection.layout.label.lowercase()}" else null,
                        { option == selected }) {
                        selected = option
                        page.refresh()
                    }
                }, "You can change this later in Menu \u203a Controls \u203a Controller layout."))
            },
            footerButtons = {
                listOf(FooterButton("continue", { "Continue" }, ButtonTone.PRIMARY, { selected != null },
                    "Choose a layout to continue") {
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
    data class Resolution<T>(val bindings: List<T>, val fallback: Boolean)
    fun <T> resolve(layout: ControllerLayout, withoutSticks: List<T>, withSticks: List<T>?): Resolution<T> =
        if (layout == ControllerLayout.WITH_STICKS && withSticks != null) Resolution(withSticks, false)
        else Resolution(withoutSticks, layout == ControllerLayout.WITH_STICKS)
}
