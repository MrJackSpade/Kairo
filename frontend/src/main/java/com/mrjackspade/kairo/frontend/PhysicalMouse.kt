package com.mrjackspade.kairo.frontend

import android.content.Context
import android.content.SharedPreferences
import android.view.MotionEvent
import android.view.View

/**
 * A Bluetooth or USB mouse as the guest's mouse (Kairo#175). While the guest is on screen, the
 * game view holds pointer capture: the mouse's own counts and buttons go to the guest
 * ([PhysicalMouseInput]), with none of the touch rules. Whenever the app's UI is up, capture is
 * given back and Android's cursor works the menus.
 */
class PhysicalMouse(private val context: Context,
                    mouse: MouseInputRouter,
                    private val preferences: SharedPreferences,
                    middleButton: Boolean,
                    wheel: ((Int) -> Unit)?) {
    private val input = PhysicalMouseInput(mouse, middleButton, wheel) { speed }
    private var view: View? = null
    /** Whether the guest is on screen and should have the mouse. */
    private var guest = false

    /** Counts sent to the guest per count the mouse reports. */
    var speed: Float
        get() = preferences.getInt(SPEED_KEY, DEFAULT_SPEED) / 10f
        private set(value) { preferences.edit().putInt(SPEED_KEY, (value * 10).toInt()).apply() }

    /** The view captured events arrive at; a replaced game surface attaches its successor. */
    fun attach(target: View) {
        view?.let { old ->
            if (old.hasPointerCapture()) old.releasePointerCapture()
            old.setOnCapturedPointerListener(null)
        }
        input.releaseAll()
        view = target
        target.setOnCapturedPointerListener { _, event -> captured(event) }
        update(guest)
    }

    /** Takes the mouse for the guest while [showingGuest], and gives it back otherwise. */
    fun update(showingGuest: Boolean) {
        guest = showingGuest
        val target = view ?: return
        if (showingGuest) {
            // Capture needs the window's focus; focus coming back calls this again.
            if (target.hasPointerCapture() || !target.hasWindowFocus()) return
            // Captured events go to the focused view.
            target.requestFocus()
            target.requestPointerCapture()
        } else {
            if (target.hasPointerCapture()) target.releasePointerCapture()
            input.releaseAll()
        }
    }

    /** The window lost or regained focus; Android drops capture with the focus. */
    fun windowFocusChanged(hasFocus: Boolean) {
        if (hasFocus) update(guest) else input.releaseAll()
    }

    fun releaseDevice(deviceId: Int) = input.releaseDevice(deviceId)

    private fun captured(event: MotionEvent): Boolean {
        if (!guest) return false
        when (event.actionMasked) {
            // These repeat the movement of the event they come with.
            MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.ACTION_BUTTON_RELEASE -> Unit
            MotionEvent.ACTION_SCROLL -> input.scroll(event.getAxisValue(MotionEvent.AXIS_VSCROLL))
            else -> {
                // A captured mouse reports relative counts as its coordinates.
                var dx = event.x
                var dy = event.y
                for (i in 0 until event.historySize) {
                    dx += event.getHistoricalX(i)
                    dy += event.getHistoricalY(i)
                }
                input.move(dx, dy)
            }
        }
        input.buttons(event.deviceId, event.buttonState)
        return true
    }

    /** Controls › MOUSE, for all games. */
    fun globalGroup() = RowGroup(context.getString(R.string.kairo_mouse_group), listOf(
        SettingsRow.slider("mouse-speed", context.getString(R.string.kairo_target_mouse_speed),
            { context.getString(R.string.kairo_target_mouse_speed_value, speed) },
            SliderSpec(1, 50, 1, 5, { (speed * 10).toInt() }) { speed = it / 10f })))

    private companion object {
        const val SPEED_KEY = "physical_mouse_speed"
        /** Tenths: 1.0×. */
        const val DEFAULT_SPEED = 10
    }
}
