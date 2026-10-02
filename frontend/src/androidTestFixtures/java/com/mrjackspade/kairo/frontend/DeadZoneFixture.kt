package com.mrjackspade.kairo.frontend

import android.app.Instrumentation
import android.content.Context
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent

object DeadZoneFixture {
    fun verify(test: Instrumentation) {
        val prefs = test.targetContext.getSharedPreferences("deadzone_test_only", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        var failure: Throwable? = null
        test.runOnMainSync {
            try {
                fun store() = ControllerProfileStore(prefs, { emptyList() }, { "[]" })
                val keys = InputRouter({ _, _ -> })
                val mouse = MouseInputRouter({ _, _ -> }, { _, _ -> })
                val mapper = GamepadMapper(keys, JoystickInputRouter({ _, _ -> }), mouse, {}, {})
                val profiles = store()
                check(profiles.deadZone == .10f && mapper.deadZone == .10f)
                mapper.physicalBindings = emptyList()
                mapper.bindings = listOf(ControllerBinding("axis:0:+", keys = listOf(1)))
                fun axis(value: Float) {
                    val now = SystemClock.uptimeMillis()
                    val p = MotionEvent.PointerProperties().apply { id = 0 }
                    val c = MotionEvent.PointerCoords().apply { setAxisValue(MotionEvent.AXIS_X, value) }
                    val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_MOVE, 1, arrayOf(p), arrayOf(c),
                        0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_JOYSTICK, 0)
                    try { mapper.motion(event) } finally { event.recycle() }
                }
                axis(0f); check(keys.pressedKeys().isEmpty())
                axis(.09f); check(keys.pressedKeys().isEmpty())
                axis(.11f); check(keys.pressedKeys() == setOf(1))
                axis(0f); check(keys.pressedKeys().isEmpty())
                profiles.deadZone = .42f
                mapper.deadZone = store().deadZone
                check(mapper.deadZone == .42f)
                axis(.20f); check(keys.pressedKeys().isEmpty())
                axis(.43f); check(keys.pressedKeys() == setOf(1))
                axis(0f); check(keys.pressedKeys().isEmpty())
                // Explicit reset and mouse-axis center behavior use the same default.
                profiles.deadZone = GamepadMapper.DEFAULT_DEAD_ZONE
                mapper.deadZone = store().deadZone
                mapper.bindings = listOf(ControllerBinding("axis:0:+", mouse = "moveRight"))
                axis(.09f); check(!mouse.hasMovement())
                axis(.11f); check(mouse.hasMovement())
                axis(0f); check(!mouse.hasMovement())
                mapper.releaseAll()
            } catch (caught: Throwable) { failure = caught }
        }
        prefs.edit().clear().commit()
        failure?.let { throw it }
    }
}
