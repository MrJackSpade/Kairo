package com.mrjackspade.kairo.frontend

import android.content.Context
import android.content.SharedPreferences
import android.view.MotionEvent
import android.view.View

/**
 * A Bluetooth or USB mouse as the guest's mouse (Kairo#175). While the guest is on screen, the
 * window with the focus holds pointer capture: the mouse's own counts and buttons go to the guest
 * ([PhysicalMouseInput]), with none of the touch rules. Whenever the app's UI is up, capture is
 * given back and Android's cursor works the menus. Capture belongs to the focused window, so on
 * two screens (the RG DS) the lower screen's window takes it once a touch gives it the focus
 * ([secondaryWindow], Kairo#177).
 */
class PhysicalMouse(private val context: Context,
                    mouse: MouseInputRouter,
                    private val preferences: SharedPreferences,
                    middleButton: Boolean,
                    wheel: ((Int) -> Unit)?) {
    private val input = PhysicalMouseInput(mouse, middleButton, wheel) { speed }
    private var view: View? = null
    /** The second screen's window, which captures while it has the focus. */
    private var secondary: View? = null
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

    /**
     * The second screen's window ([target], null once it closes) gained or lost the focus. Its view
     * takes focus to receive captured events, so it is made focusable in touch mode.
     */
    fun secondaryWindow(target: View?, hasFocus: Boolean) {
        if (secondary !== target) {
            secondary?.let { old ->
                if (old.hasPointerCapture()) old.releasePointerCapture()
                old.setOnCapturedPointerListener(null)
            }
            secondary = target
            target?.isFocusableInTouchMode = true
            target?.setOnCapturedPointerListener { _, event -> captured(event) }
        }
        windowFocusChanged(hasFocus)
    }

    /** Takes the mouse for the guest while [showingGuest], and gives it back otherwise. */
    fun update(showingGuest: Boolean) {
        guest = showingGuest
        val targets = listOfNotNull(view, secondary)
        // Capture needs the window's focus; focus coming back to either window calls this again.
        val focused = targets.firstOrNull { it.hasWindowFocus() }
        // Released whether or not capture has arrived yet: a request still on its way (a window that
        // just took the focus, with the menu opening on it) is withdrawn with it.
        for (target in targets)
            if (!showingGuest || target !== focused) target.releasePointerCapture()
        if (!showingGuest) {
            input.releaseAll()
            return
        }
        if (focused == null || focused.hasPointerCapture()) return
        // Captured events go to the focused view.
        focused.requestFocus()
        focused.requestPointerCapture()
    }

    /** A window lost or regained focus; Android drops capture with the focus. */
    fun windowFocusChanged(hasFocus: Boolean) {
        if (!hasFocus) input.releaseAll()
        update(guest)
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
