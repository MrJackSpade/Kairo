package com.mrjackspade.kairo.frontend

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.abs

/** Captures every controller button, B included, and stick, trigger, or hat movement as a physical input such as button:96 or axis:0:+. */
class PhysicalInputCapture(private val captured: (String) -> Unit) : InputCapture {
    private val baseline = HashMap<Pair<Int, Int>, Float>()

    override fun key(event: KeyEvent): Boolean {
        if (!KeyEvent.isGamepadButton(event.keyCode) && !event.isFromSource(InputDevice.SOURCE_GAMEPAD) &&
            !event.isFromSource(InputDevice.SOURCE_JOYSTICK)) return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) captured("button:" + event.keyCode)
        return true
    }

    override fun motion(event: MotionEvent): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK) &&
            !event.isFromSource(InputDevice.SOURCE_GAMEPAD)) return true
        val axes = event.device?.motionRanges?.filter {
            it.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
        }?.map { it.axis }?.distinct() ?: emptyList()
        for (axis in axes) {
            val value = event.getAxisValue(axis)
            val id = event.deviceId to axis
            val start = baseline[id]
            val trigger = axis == MotionEvent.AXIS_LTRIGGER || axis == MotionEvent.AXIS_RTRIGGER ||
                axis == MotionEvent.AXIS_BRAKE || axis == MotionEvent.AXIS_GAS
            if (start == null) {
                baseline[id] = value
                if (abs(value) >= 0.75f && (!trigger || value > 0.75f)) {
                    captured(InputSources.axisInput(axis, value))
                    return true
                }
            } else {
                if (abs(value) < 0.25f) baseline[id] = value
                if (abs(value) >= 0.75f && abs(value - start) >= 0.75f && (!trigger || value > 0.75f)) {
                    captured(InputSources.axisInput(axis, value))
                    return true
                }
            }
        }
        return true
    }
}
