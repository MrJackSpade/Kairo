package com.mrjackspade.kairo.frontend

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler

/** Releases a controller's held guest inputs when Android replaces or removes it. */
class ControllerDeviceMonitor(context: Context, private val releaseDevice: (Int) -> Unit) {
    private val manager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
    private val listener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = Unit
        override fun onInputDeviceChanged(deviceId: Int) { releaseDevice(deviceId) }
        override fun onInputDeviceRemoved(deviceId: Int) { releaseDevice(deviceId) }
    }
    private var registered = false

    fun register(handler: Handler) {
        if (registered) return
        manager.registerInputDeviceListener(listener, handler)
        registered = true
    }

    fun unregister() {
        if (!registered) return
        manager.unregisterInputDeviceListener(listener)
        registered = false
    }
}
