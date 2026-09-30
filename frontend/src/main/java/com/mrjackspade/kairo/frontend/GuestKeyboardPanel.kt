package com.mrjackspade.kairo.frontend

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView

/** Fitted, paged guest keyboard. Every key uses the same guest-key input router. */
class GuestKeyboardPanel(
    context: Context,
    private val input: InputRouter,
    private val layout: GuestKeyboardLayout,
    private val onClose: () -> Unit,
    private val showClose: Boolean = true,
    private val onSwap: (() -> Unit)? = null,
    mouse: MouseInputRouter? = null,
    mouseReferenceSize: () -> Pair<Int, Int> = { 640 to 400 },
    private val onVisibilityChanged: (() -> Unit)? = null
) : LinearLayout(context) {
    private companion object { const val TOUCHPAD_PAGE = -1 }

    private val handler = Handler(Looper.getMainLooper())
    private val latched = mutableSetOf<Int>()
    private val keyViews = mutableListOf<Pair<KeyboardKey, TextView>>()
    private val pageViews = mutableMapOf<Int, View>()
    private val touchpad = mouse?.let { SecondaryTouchpadView(context, it, mouseReferenceSize) }
    private val content = LinearLayout(context).apply { orientation = VERTICAL }
    private val highlightUntil = mutableMapOf<Int, Long>()
    private var lastPressed = emptySet<Int>()
    private val inputListener: () -> Unit = {
        if (Looper.myLooper() == Looper.getMainLooper()) updateHighlights()
        else handler.post { updateHighlights() }
    }
    private var page = 0
    private val visibilityListeners = LinkedHashSet<() -> Unit>()

    fun addVisibilityListener(listener: () -> Unit) { visibilityListeners.add(listener) }
    fun removeVisibilityListener(listener: () -> Unit) { visibilityListeners.remove(listener) }

    private fun notifyVisibilityChanged() {
        onVisibilityChanged?.invoke()
        visibilityListeners.toList().forEach { it() }
    }

    init {
        orientation = VERTICAL
        if (onSwap != null) gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        setBackgroundColor(Ui.SURFACE)
        elevation = dp(14).toFloat()
        visibility = View.GONE

        if (onSwap != null) {
            val brand = LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(4), dp(8), dp(4))
            }
            brand.addView(PixelTextView(context).apply { text = layout.brand }, LayoutParams(0, -2, 1f))
            brand.addView(TextView(context).apply {
                text = "\u2191\u2193"
                contentDescription = "Swap game and keyboard screens"
                gravity = Gravity.CENTER
                textSize = 22f
                setTextColor(keyText())
                background = keyBackground(action = true)
                setOnClickListener { onSwap.invoke() }
            }, LayoutParams(dp(54), dp(40)))
            addView(brand, LayoutParams(-1, dp(48)))
        }

        val header = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(3), dp(6), dp(3))
        }
        for ((target, keyboardPage) in layout.pages.withIndex()) {
            val tab = TextView(context).apply {
                text = keyboardPage.label
                gravity = Gravity.CENTER
                textSize = Ui.SECONDARY
                setTextColor(keyText())
                attachPageSwitch(this, target)
            }
            tab.background = keyBackground(action = true)
            pageViews[target] = tab
            header.addView(tab, LayoutParams(0, dp(34), 1f).apply {
                setMargins(dp(2), 0, dp(2), 0)
            })
        }
        if (touchpad != null) {
            val tab = ImageView(context).apply {
                setImageResource(R.drawable.ic_mouse)
                imageTintList = keyText()
                setPadding(dp(10), dp(7), dp(10), dp(7))
                contentDescription = "Mouse touchpad mode"
                background = keyBackground(action = true)
                attachPageSwitch(this, TOUCHPAD_PAGE)
            }
            pageViews[TOUCHPAD_PAGE] = tab
            header.addView(tab, LayoutParams(0, dp(34), 1f).apply {
                setMargins(dp(2), 0, dp(2), 0)
            })
        }
        if (showClose) {
            header.addView(TextView(context).apply {
                text = "Close ×"
                gravity = Gravity.CENTER
                textSize = 14f
                setTextColor(Ui.ACCENT_SOFT)
                setOnClickListener { onClose() }
            }, LayoutParams(dp(76), dp(34)))
        }
        addView(header, LayoutParams(-1, dp(42)))
        addView(content, if (onSwap == null) LayoutParams(-1, 0, 1f)
            else LayoutParams(-1, dp(320)))
        showPage(0)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        input.addListener(inputListener)
        updateHighlights()
    }

    override fun onDetachedFromWindow() {
        input.removeListener(inputListener)
        handler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        if (Build.VERSION.SDK_INT >= 30 && visibility == View.VISIBLE) {
            if (insets.isVisible(WindowInsets.Type.ime())) close()
        }
        return super.onApplyWindowInsets(insets)
    }

    fun open() {
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(rootView.windowToken, 0)
        visibility = View.VISIBLE
        notifyVisibilityChanged()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        updateContentSize()
    }

    private fun updateContentSize() {
        if (onSwap == null || width <= 0 || height <= 0) return
        val availableHeight = (height - dp(90)).coerceAtLeast(0)
        val rowsHeight = if (page == TOUCHPAD_PAGE) availableHeight else minOf(dp(320), availableHeight)
        val rowsWidth = if (page == TOUCHPAD_PAGE) width else minOf(width, dp(900))
        if (content.layoutParams.height != rowsHeight || content.layoutParams.width != rowsWidth)
            content.layoutParams = LayoutParams(rowsWidth, rowsHeight)
    }

    fun close() {
        visibility = View.GONE
        touchpad?.close()
        input.releasePrefix("touch-key:")
        input.releasePrefix("touch-mod:")
        latched.clear()
        highlightUntil.clear()
        lastPressed = emptySet()
        updateLegends()
        notifyVisibilityChanged()
    }

    private fun attachPageSwitch(tab: View, target: Int) {
        tab.setOnClickListener { if (page != target) showPage(target) }
        tab.setOnTouchListener { _, event ->
            // A focus change can cancel ACTION_UP on a second display. Switch on press
            // while retaining the normal click path for keyboard and accessibility input.
            if (event.actionMasked == MotionEvent.ACTION_DOWN && page != target) showPage(target)
            false
        }
    }

    private fun showPage(target: Int) {
        if (page == TOUCHPAD_PAGE) touchpad?.close()
        input.releasePrefix("touch-key:")
        if (page != target) {
            for (shift in layout.shiftCodes) {
                if (latched.remove(shift)) input.release("touch-mod:$shift")
            }
        }
        page = target
        updateContentSize()
        content.removeAllViews()
        keyViews.clear()
        if (target == TOUCHPAD_PAGE) {
            touchpad?.let { content.addView(it, LayoutParams(-1, -1)) }
            pageViews.forEach { (name, view) -> view.isActivated = name == page }
            return
        }
        layout.pages[page].rows.forEach(::row)
        pageViews.forEach { (name, view) -> view.isActivated = name == page }
        updateLegends()
    }

    fun setInitialMode(touchpadMode: Boolean) {
        if (touchpad != null) showPage(if (touchpadMode) TOUCHPAD_PAGE else 0)
    }

    private fun row(keys: List<KeyboardKey>) {
        val line = LinearLayout(context).apply { orientation = HORIZONTAL }
        for (key in keys) {
            val modifier = key.code in layout.modifiers
            val holdToLatch = modifier && key.code != layout.capsCode
            // Letters, digits, and symbols sit on the lighter key; named keys are darker.
            val action = modifier || key.label.length > 1
            val view = TextView(context).apply {
                gravity = Gravity.CENTER
                setTextColor(keyText())
                setAutoSizeTextTypeUniformWithConfiguration(9, 16, 1, TypedValue.COMPLEX_UNIT_SP)
                isClickable = true
                setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            if (holdToLatch) {
                                if (key.code in latched) toggleModifier(key.code)
                                press(key)
                            } else if (modifier) toggleModifier(key.code)
                            else press(key)
                            true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            if (holdToLatch) {
                                if (event.actionMasked == MotionEvent.ACTION_UP &&
                                    event.eventTime - event.downTime >= ViewConfiguration.getLongPressTimeout())
                                    toggleModifier(key.code)
                                release(key)
                            } else if (!modifier) release(key)
                            true
                        }
                        else -> true
                    }
                }
                setOnClickListener {
                    if (modifier && !holdToLatch) toggleModifier(key.code)
                    else {
                        if (key.code in latched) toggleModifier(key.code)
                        press(key)
                        handler.postDelayed({ release(key) }, 90)
                    }
                }
                if (holdToLatch) setOnLongClickListener {
                    toggleModifier(key.code)
                    true
                }
            }
            view.background = keyBackground(action)
            keyViews.add(key to view)
            line.addView(view, LayoutParams(0, -1, key.width).apply {
                setMargins(dp(2), dp(2), dp(2), dp(2))
            })
        }
        content.addView(line, LayoutParams(-1, 0, 1f))
    }

    private fun press(key: KeyboardKey) {
        highlightUntil[key.code] = SystemClock.uptimeMillis() + 90
        input.hold("touch-key:${key.code}", if (key.chordShift) listOf(layout.chordShiftCode, key.code)
            else listOf(key.code))
    }

    private fun release(key: KeyboardKey) {
        input.release("touch-key:${key.code}")
        val delay = (highlightUntil[key.code] ?: 0L) - SystemClock.uptimeMillis()
        if (delay > 0) handler.postDelayed({ updateHighlights() }, delay)
    }

    private fun toggleModifier(scan: Int) {
        if (latched.remove(scan)) input.release("touch-mod:$scan")
        else {
            latched.add(scan)
            input.hold("touch-mod:$scan", listOf(scan))
        }
        updateLegends()
    }

    private fun updateLegends() {
        val shifted = layout.shiftCodes.any { it in latched }
        val caps = layout.capsCode?.let { it in latched } == true
        for ((key, view) in keyViews) {
            val letter = key.label.length == 1 && key.label[0] in 'a'..'z'
            view.text = when {
                letter && (shifted xor caps) -> key.label.uppercase()
                key.shifted != null && shifted -> key.shifted
                else -> key.label
            }
            view.isActivated = key.code in latched
        }
        updateHighlights()
    }

    private fun updateHighlights() {
        val pressed = input.pressedKeys()
        val now = SystemClock.uptimeMillis()
        for (scan in pressed - lastPressed)
            highlightUntil[scan] = now + 90
        for (scan in lastPressed - pressed) {
            val delay = (highlightUntil[scan] ?: 0L) - now
            if (delay > 0) handler.postDelayed({ updateHighlights() }, delay)
        }
        lastPressed = pressed
        val shifted = layout.shiftCodes.any { it in pressed }
        highlightUntil.entries.removeAll { it.value <= now }
        for ((key, view) in keyViews) {
            val matchingShift = !key.chordShift || shifted
            view.isPressed = matchingShift &&
                (key.code in pressed || (highlightUntil[key.code] ?: 0L) > now)
        }
    }

    /** Latched modifiers and the current page fill with the accent; everything else stays quiet. */
    private fun keyBackground(action: Boolean = false): RippleDrawable {
        val states = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_activated), keyShape(Ui.ACCENT))
            addState(intArrayOf(android.R.attr.state_pressed), keyShape(Ui.SELECTED))
            addState(intArrayOf(), if (action) keyShape(Ui.SURFACE, Ui.LINE) else keyShape(Ui.RAISED))
        }
        return RippleDrawable(ColorStateList.valueOf(0x80a8eef1.toInt()), states, null)
    }

    private fun keyText() = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_activated), intArrayOf()), intArrayOf(Ui.ON_ACCENT, Ui.TEXT))

    private fun keyShape(color: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(5).toFloat()
        stroke?.let { setStroke(dp(1), it) }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

}
