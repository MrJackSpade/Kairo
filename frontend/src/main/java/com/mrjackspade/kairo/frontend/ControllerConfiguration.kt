package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.app.AlertDialog
import android.content.SharedPreferences
import android.os.Build
import android.view.InputDevice
import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

enum class ControllerLayout(val label: String, val key: String) {
    WITH_STICKS("With Sticks", "withSticks"),
    WITHOUT_STICKS("Without Sticks", "withoutSticks")
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
    enum class Choice(val label: String) { AUTO("Auto"), WITH_STICKS("With Sticks"), WITHOUT_STICKS("Without Sticks") }
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
    val label: String get() = if (choice == Choice.AUTO) "Auto (${layout.label})" else choice.label

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

    /** No selection is made when no controller is detected, including upgraded installations. */
    fun show(activity: Activity, required: Boolean = false, onSelected: () -> Unit) {
        val detection = detect()
        val choices = if (detection.device == null) listOf(Choice.WITH_STICKS, Choice.WITHOUT_STICKS)
            else Choice.entries
        var selected: Choice? = if (required) {
            if (detection.device == null) null else Choice.AUTO
        } else choice.takeIf { it in choices }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(activity, 20), Ui.dp(activity, 16), Ui.dp(activity, 20), Ui.dp(activity, 16))
        }
        content.addView(Ui.text(activity, if (detection.device == null)
            "No controller detected. Select a configuration to continue."
            else "Select your controller configuration.", Ui.BODY, Ui.TEXT_BODY))
        val rows = mutableMapOf<Choice, TextView>()
        val dialog = AlertDialog.Builder(activity).setTitle("Controller configuration")
            .setView(ScrollView(activity).apply { addView(content) })
            .setPositiveButton(if (required) "Continue" else "Apply", null)
            .apply { if (!required) setNegativeButton("Cancel", null) }
            .create()
        choices.forEach { option ->
            val row = Ui.text(activity, if (option == Choice.AUTO)
                "Auto (detected: ${detection.layout.label})" else option.label, Ui.TITLE).apply {
                isFocusable = true
                isFocusableInTouchMode = true
                isClickable = true
                isActivated = option == selected
                background = Ui.rowBackground(activity)
                setPadding(Ui.dp(activity, 16), Ui.dp(activity, 18), Ui.dp(activity, 16), Ui.dp(activity, 18))
                setOnClickListener {
                    selected = option
                    rows.forEach { (key, view) -> view.isActivated = key == option }
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                }
            }
            rows[option] = row
            content.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(activity, 12) })
        }
        dialog.setCancelable(true)
        if (required) dialog.setOnCancelListener { activity.finish() }
        dialog.setCanceledOnTouchOutside(!required)
        dialog.show()
        Ui.styleDialog(dialog)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
            isEnabled = selected != null
            setOnClickListener {
                selected?.let {
                    if (it == Choice.AUTO && detect().device == null) {
                        dialog.dismiss()
                        show(activity, required, onSelected)
                    } else {
                        select(it)
                        dialog.dismiss()
                        onSelected()
                    }
                }
            }
        }
        // Required setup is a full-screen page; the separate window also owns controller navigation.
        if (required) dialog.window?.setLayout(-1, -1)
        rows[selected ?: choices.first()]?.requestFocusFromTouch()
    }
}

/** Missing variants fall back; an explicitly empty list is an intentional mapping. */
object ControllerDefaults {
    data class Resolution<T>(val bindings: List<T>, val fallback: Boolean)
    fun <T> resolve(layout: ControllerLayout, withoutSticks: List<T>, withSticks: List<T>?): Resolution<T> =
        if (layout == ControllerLayout.WITH_STICKS && withSticks != null) Resolution(withSticks, false)
        else Resolution(withoutSticks, layout == ControllerLayout.WITH_STICKS)
}
