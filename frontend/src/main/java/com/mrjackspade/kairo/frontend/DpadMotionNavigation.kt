package com.mrjackspade.kairo.frontend

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.abs

/** Convert controller hat axes to ordinary menu keys, including held repeat. */
internal class DpadMotionNavigation(private val send: (KeyEvent) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var key: Int? = null
    private var downTime = 0L
    private var repeats = 0
    private val repeat = object : Runnable {
        override fun run() {
            key?.let { emit(it, KeyEvent.ACTION_DOWN, ++repeats) } ?: return
            if (key != null) handler.postDelayed(this, 120)
        }
    }
    private fun emit(code: Int, action: Int, count: Int = 0) = send(KeyEvent(
        downTime, SystemClock.uptimeMillis(), action, code, count, 0, -1, 0, 0,
        InputDevice.SOURCE_DPAD))
    fun stop() {
        handler.removeCallbacks(repeat)
        val old = key
        key = null
        old?.let { emit(it, KeyEvent.ACTION_UP) }
    }
    fun motion(event: MotionEvent): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK) &&
            !event.isFromSource(InputDevice.SOURCE_GAMEPAD)) return false
        val x = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val y = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val next = when {
            abs(y) >= .5f -> if (y < 0) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN
            abs(x) >= .5f -> if (x < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
            else -> null
        }
        if (next != key) {
            stop()
            key = next
            downTime = SystemClock.uptimeMillis()
            repeats = 0
            if (next != null) { emit(next, KeyEvent.ACTION_DOWN); handler.postDelayed(repeat, 400) }
        }
        return true
    }
}
