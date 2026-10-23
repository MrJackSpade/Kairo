package com.mrjackspade.kairo.frontend

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable

/**
 * What a guest keyboard panel drives and shows: guest keys, the touchpad's mouse, the guest
 * joystick, controller mapping, and the controller presses whose bindings it names.
 */
class GuestPanelInputs(
    val keys: InputRouter,
    val mouse: MouseInputRouter?,
    val joystick: JoystickInputRouter?,
    val spec: ControllerGuestSpec?,
    val mapping: KeyMappingMode?,
    val gamepad: GamepadMapper?
) {
    /** The Joystick tab appears for a guest that has joystick controls. */
    val hasJoystick: Boolean get() = joystick != null && spec?.joystickControls?.isNotEmpty() == true
}

/** The look of a panel key, shared by the keyboard and joystick pages. */
internal object PanelKeyStyle {
    /** Latched modifiers and the current page fill with the accent; everything else stays quiet. */
    fun keyBackground(context: Context, action: Boolean = false): RippleDrawable {
        val states = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_activated), shape(context, Ui.ACCENT))
            addState(intArrayOf(android.R.attr.state_pressed), shape(context, Ui.SELECTED))
            addState(intArrayOf(), if (action) shape(context, Ui.SURFACE, Ui.LINE) else shape(context, Ui.RAISED))
        }
        return RippleDrawable(ColorStateList.valueOf(0x80a8eef1.toInt()), states, null)
    }

    fun mappingBackground(context: Context, fill: Int, stroke: Int?): RippleDrawable {
        val states = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), shape(context, Ui.SELECTED, stroke))
            addState(intArrayOf(), shape(context, fill, stroke))
        }
        return RippleDrawable(ColorStateList.valueOf(0x80a8eef1.toInt()), states, null)
    }

    fun keyText() = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_activated), intArrayOf()), intArrayOf(Ui.ON_ACCENT, Ui.TEXT))

    private fun shape(context: Context, color: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = Ui.dp(context, 5).toFloat()
        stroke?.let { setStroke(Ui.dp(context, 1), it) }
    }
}
