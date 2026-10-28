package com.mrjackspade.kairo.frontend

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The keyboard panel's Joystick tab: each port's directions as a pad and its buttons, which
 * light while held from any source, send when touched, and map like keys in mapping mode.
 */
@SuppressLint("ViewConstructor")
internal class GuestJoystickPage(
    context: Context,
    private val joystick: JoystickInputRouter,
    private val spec: ControllerGuestSpec,
    private val mapping: KeyMappingMode?
) : LinearLayout(context) {
    private val handler = Handler(Looper.getMainLooper())
    private val controlViews = mutableListOf<Pair<String, TextView>>()
    private val joystickListener: () -> Unit = {
        if (Looper.myLooper() == Looper.getMainLooper()) updatePressed() else handler.post { updatePressed() }
    }
    private val mappingListener: () -> Unit = { updateMapping() }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(8), dp(4), dp(8), dp(4))
        for (port in spec.joystickPorts) addView(portView(port), LayoutParams(-1, 0, 1f))
    }

    private fun portView(port: JoystickPort): View {
        val column = LinearLayout(context).apply { orientation = VERTICAL }
        port.label?.let { column.addView(Ui.text(context, it, Ui.SECONDARY, Ui.TEXT_MUTED).apply { gravity = Gravity.CENTER }) }
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
        }
        val pad = LinearLayout(context).apply { orientation = VERTICAL }
        for (line in listOf(listOf(null, port.up, null), listOf(port.left, null, port.right), listOf(null, port.down, null))) {
            val cells = LinearLayout(context).apply { orientation = HORIZONTAL }
            for (id in line) cells.addView(id?.let { control(it, arrow(it, port)) } ?: View(context),
                LayoutParams(0, -1, 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
            pad.addView(cells, LayoutParams(-1, 0, 1f))
        }
        row.addView(pad, LayoutParams(0, -1, 1f))
        val buttons = LinearLayout(context).apply { orientation = VERTICAL }
        for (pair in port.buttons.chunked(2)) {
            val cells = LinearLayout(context).apply { orientation = HORIZONTAL }
            for (id in pair) cells.addView(control(id, spec.joystickLabel(context, id)),
                LayoutParams(0, -1, 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
            buttons.addView(cells, LayoutParams(-1, 0, 1f))
        }
        row.addView(buttons, LayoutParams(0, -1, 1f).apply { marginStart = dp(16) })
        column.addView(row, LayoutParams(-1, 0, 1f))
        return column
    }

    private fun arrow(id: String, port: JoystickPort) = when (id) {
        port.up -> "↑"
        port.down -> "↓"
        port.left -> "←"
        else -> "→"
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun control(id: String, label: String): TextView = TextView(context).apply {
        text = label
        contentDescription = spec.joystickTarget(context, id)
        testId("joystick.control", id)
        gravity = Gravity.CENTER
        maxLines = 2
        setTextColor(PanelKeyStyle.keyText())
        setAutoSizeTextTypeUniformWithConfiguration(9, 18, 1, TypedValue.COMPLEX_UNIT_SP)
        background = PanelKeyStyle.keyBackground(context, action = label.length > 1)
        isClickable = true
        setOnTouchListener { _, event ->
            if (mapping?.active == true) {
                if (event.actionMasked == MotionEvent.ACTION_DOWN) mapping.tapJoystick(id)
                return@setOnTouchListener true
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> joystick.hold(owner(id), id)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> joystick.release(owner(id))
            }
            true
        }
        // Accessibility clicks press and release the control.
        setOnClickListener {
            if (mapping?.active == true) mapping.tapJoystick(id)
            else {
                joystick.hold(owner(id), id)
                handler.postDelayed({ joystick.release(owner(id)) }, 90)
            }
        }
    }.also { controlViews += id to it }

    private fun owner(id: String) = "$OWNER$id"

    private fun updatePressed() {
        val pressed = joystick.pressed()
        for ((id, view) in controlViews) view.isPressed = id in pressed
    }

    /** Mapping mode colors the waiting control and every mapped one, as on the keyboard. */
    private fun updateMapping() {
        val mode = mapping ?: return
        for ((id, view) in controlViews) {
            val waiting = mode.active && mode.waitingJoystick == id
            view.background = when {
                waiting -> PanelKeyStyle.mappingBackground(context, Ui.PIN, null)
                mode.active && mode.mappedJoystick(id) -> PanelKeyStyle.mappingBackground(context, Ui.PIN_SURFACE, Ui.PIN)
                else -> PanelKeyStyle.keyBackground(context, action = view.text.length > 1)
            }
            if (waiting) view.setTextColor(Ui.ON_ACCENT) else view.setTextColor(PanelKeyStyle.keyText())
        }
    }

    /** Lets go of every control this page holds. */
    fun releaseAll() = joystick.releasePrefix(OWNER)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        joystick.addListener(joystickListener)
        mapping?.addListener(mappingListener)
        updatePressed()
        updateMapping()
    }

    override fun onDetachedFromWindow() {
        joystick.removeListener(joystickListener)
        mapping?.removeListener(mappingListener)
        releaseAll()
        handler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }

    private fun dp(value: Int) = Ui.dp(context, value)

    private companion object { const val OWNER = "touch-joy:" }
}
