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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView

/** Fitted, paged guest keyboard, with the guest's joystick and touchpad as further tabs. */
class GuestKeyboardPanel(
    context: Context,
    private val inputs: GuestPanelInputs,
    private val layout: GuestKeyboardLayout,
    private val onClose: () -> Unit,
    private val showClose: Boolean = true,
    private val onSwap: (() -> Unit)? = null,
    /** Whether the touchpad tab is offered, given a mouse in [inputs]. */
    touchpadTab: Boolean = true,
    mouseReferenceSize: () -> Pair<Int, Int> = { 640 to 400 },
    private val onVisibilityChanged: (() -> Unit)? = null
) : LinearLayout(context) {
    private companion object {
        const val TOUCHPAD_PAGE = -1
        const val JOYSTICK_PAGE = -2
    }

    private val input = inputs.keys
    private val mapping = inputs.mapping

    private val handler = Handler(Looper.getMainLooper())
    private val latched = mutableSetOf<Int>()
    private val keyViews = mutableListOf<Pair<KeyboardKey, TextView>>()
    private val pageViews = mutableMapOf<Int, View>()
    private val touchpad = inputs.mouse?.takeIf { touchpadTab }?.let { SecondaryTouchpadView(context, it, mouseReferenceSize) }
    private val joystickPage = if (inputs.hasJoystick)
        GuestJoystickPage(context, inputs.joystick!!, inputs.spec!!, mapping) else null
    /** Names a press whose binding is not a key or joystick control, such as a mouse button or Menu. */
    private val pressedLabel = Ui.text(context, "", Ui.SECONDARY, Ui.ON_ACCENT, bold = true).apply {
        visibility = View.GONE
        setPadding(dp(10), dp(4), dp(10), dp(4))
        background = GradientDrawable().apply { setColor(Ui.ACCENT); cornerRadius = dp(5).toFloat() }
    }
    private val hidePressedLabel = Runnable { pressedLabel.visibility = View.GONE }
    private val activationListener: (ControllerBinding) -> Unit = { binding ->
        if (binding.action != null || binding.mouse != null) handler.post { showPressed(binding) }
    }
    private val content = LinearLayout(context).apply { orientation = VERTICAL }
    /** The key rows or touchpad, with the mapping prompt over them. */
    private val stage = FrameLayout(context)
    private val keyActions = mutableMapOf<TextView, Boolean>()
    private var mappingTab: View? = null
    private var mappingPrompt: View? = null
    private var mappingShown = false
    private val mappingListener: () -> Unit = { mappingChanged() }
    /** A scan code lit after its press, and whether Shift was down for it. */
    private class Lit(val until: Long, val shifted: Boolean)
    private val highlightUntil = mutableMapOf<Int, Lit>()
    /** Scan codes this page also offers as a Shift chord, such as 5 for %. */
    private var chordCodes = emptySet<Int>()
    private var lastPressed = emptySet<Int>()
    // Lock state can change from any source, such as a physical Caps Lock or the other screen's keyboard.
    private val inputListener: () -> Unit = {
        if (Looper.myLooper() == Looper.getMainLooper()) updateLegends()
        else handler.post { updateLegends() }
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
        // A touch surface: no key in it may take focus, or a controller press after a touch
        // would focus and light the first one (the mapping button) and could press it.
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        isFocusable = false

        // Mapping is a keyboard control, not an input page: it sits with Swap, or with Close without a second screen.
        val mappingButton = mapping?.let { mode ->
            ImageView(context).apply {
                setImageResource(R.drawable.ic_gamepad)
                imageTintList = keyText()
                setPadding(dp(14), dp(8), dp(14), dp(8))
                contentDescription = "Controller mapping"
                testId("keyboard.mapping")
                background = keyBackground(action = true)
                onPress(this) { mode.toggle() }
            }
        }
        mappingTab = mappingButton
        if (onSwap != null) {
            val brand = LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(4), dp(8), dp(4))
            }
            brand.addView(PixelTextView(context).apply { text = layout.brand; this.brand = true }, LayoutParams(0, -2, 1f))
            mappingButton?.let { brand.addView(it, LayoutParams(dp(54), dp(40)).apply { marginEnd = dp(8) }) }
            brand.addView(TextView(context).apply {
                text = "\u2191\u2193"
                contentDescription = "Swap game and keyboard screens"
                testId("keyboard.swap")
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
        if (joystickPage != null) {
            val tab = TextView(context).apply {
                text = "Joystick"
                gravity = Gravity.CENTER
                textSize = Ui.SECONDARY
                setTextColor(keyText())
                background = keyBackground(action = true)
                attachPageSwitch(this, JOYSTICK_PAGE)
            }
            pageViews[JOYSTICK_PAGE] = tab
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
        if (onSwap == null) mappingButton?.let {
            header.addView(it, LayoutParams(dp(54), dp(34)).apply { marginStart = dp(10) })
        }
        if (showClose) {
            header.addView(TextView(context).apply {
                text = "Close ×"
                testId("keyboard.close")
                gravity = Gravity.CENTER
                textSize = 14f
                setTextColor(Ui.ACCENT_SOFT)
                setOnClickListener { onClose() }
            }, LayoutParams(dp(76), dp(34)))
        }
        addView(header, LayoutParams(-1, dp(42)))
        stage.addView(content, FrameLayout.LayoutParams(-1, -1))
        stage.addView(pressedLabel, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dp(6)
        })
        if (mapping != null) mappingPrompt = mappingPrompt().also {
            stage.addView(it, FrameLayout.LayoutParams(-1, -1))
        }
        addView(stage, if (onSwap == null) LayoutParams(-1, 0, 1f)
            else LayoutParams(-1, dp(320)))
        showPage(0)
    }

    private fun mappingPrompt(): View = LinearLayout(context).apply {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        visibility = View.GONE
        isClickable = true
        setBackgroundColor(Ui.SCRIM)
        setPadding(dp(24), dp(12), dp(24), dp(12))
        addView(Ui.text(context, "Controller mapping", Ui.TITLE, bold = true).apply { gravity = Gravity.CENTER })
        addView(Ui.text(context, "Tap a key, then press the controller button that should send it. " +
            "Tap a selected key again to clear it. Tap the controller button again to finish.", Ui.BODY, Ui.TEXT_BODY).apply {
            gravity = Gravity.CENTER
        }, LayoutParams(-2, -2).apply { setMargins(0, dp(8), 0, dp(14)) })
        val ok = Ui.primaryButton(context, "OK") { visibility = View.GONE }
        onPress(ok) { visibility = View.GONE }
        addView(ok, LayoutParams(dp(120), dp(44)))
    }

    /** Acts on press: a focus change can cancel ACTION_UP on a second display. Clicks still serve accessibility. */
    private fun onPress(view: View, action: () -> Unit) {
        view.setOnClickListener { action() }
        view.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) action()
            true
        }
    }

    private fun mappingChanged() {
        val mode = mapping ?: return
        mappingTab?.isActivated = mode.active
        if (mode.active && !mappingShown) {
            mappingShown = true
            if (page == TOUCHPAD_PAGE) showPage(0)
            if (visibility == View.VISIBLE) mappingPrompt?.visibility = View.VISIBLE
        } else if (!mode.active) {
            mappingShown = false
            mappingPrompt?.visibility = View.GONE
        }
        updateMappingKeys()
    }

    private fun keyList(key: KeyboardKey) =
        if (key.chordShift) listOf(layout.chordShiftCode, key.code) else listOf(key.code)

    /** Mapping mode colors the waiting key and every mapped key; leaving it restores the normal keys. */
    private fun updateMappingKeys() {
        val mode = mapping ?: return
        for ((key, view) in keyViews) {
            val keys = keyList(key)
            val waiting = mode.active && mode.waiting == keys
            view.background = when {
                waiting -> mappingBackground(Ui.PIN, null)
                mode.active && mode.mapped(keys) -> mappingBackground(Ui.PIN_SURFACE, Ui.PIN)
                else -> keyBackground(keyActions[view] == true)
            }
            if (waiting) view.setTextColor(Ui.ON_ACCENT) else view.setTextColor(keyText())
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        input.addListener(inputListener)
        mapping?.addListener(mappingListener)
        inputs.gamepad?.addActivationListener(activationListener)
        mappingChanged()
        updateHighlights()
    }

    override fun onDetachedFromWindow() {
        input.removeListener(inputListener)
        mapping?.removeListener(mappingListener)
        inputs.gamepad?.removeActivationListener(activationListener)
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

    /** Opens or closes to match [shown]; repeated calls with the same value change nothing. */
    fun setShown(shown: Boolean) {
        val visible = visibility == View.VISIBLE
        if (shown && !visible) open() else if (!shown && visible) close()
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
        if (stage.layoutParams.height != rowsHeight || stage.layoutParams.width != rowsWidth)
            stage.layoutParams = LayoutParams(rowsWidth, rowsHeight)
    }

    fun close() {
        // Mapping belongs to the keyboard on screen; a hidden panel closing again must not end it.
        if (visibility == View.VISIBLE) mapping?.exit()
        visibility = View.GONE
        touchpad?.close()
        joystickPage?.releaseAll()
        pressedLabel.visibility = View.GONE
        input.releasePrefix("touch-key:")
        input.releasePrefix("touch-mod:")
        latched.clear()
        highlightUntil.clear()
        lastPressed = emptySet()
        updateLegends()
        notifyVisibilityChanged()
    }

    private fun attachPageSwitch(tab: View, target: Int) {
        // The tab by its page number; the touchpad's tab is the last page.
        TestAttributes.mark(tab, "keyboard.tab", target.toString())
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
        if (page == JOYSTICK_PAGE) joystickPage?.releaseAll()
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
        keyActions.clear()
        if (target == TOUCHPAD_PAGE || target == JOYSTICK_PAGE) {
            (if (target == TOUCHPAD_PAGE) touchpad else joystickPage)?.let { content.addView(it, LayoutParams(-1, -1)) }
            pageViews.forEach { (name, view) -> view.isActivated = name == page }
            return
        }
        val grid = KeyGrid(context, dp(2))
        for (cell in KeyGrid.place(layout.pages[page].rows)) grid.add(keyView(cell.key), cell)
        content.addView(grid, LayoutParams(-1, -1))
        chordCodes = keyViews.filter { it.first.chordShift }.map { it.first.code }.toSet()
        pageViews.forEach { (name, view) -> view.isActivated = name == page }
        updateLegends()
        if (mapping?.active == true) updateMappingKeys()
    }

    fun setInitialMode(touchpadMode: Boolean) {
        if (touchpad != null) showPage(if (touchpadMode) TOUCHPAD_PAGE else 0)
    }

    private fun keyView(key: KeyboardKey): TextView {
        val modifier = key.code in layout.modifiers
        val holdToLatch = modifier && !layout.isLock(key.code)
        // The press that unlocks a held modifier must not lock it again by being long.
        var unlockedOnPress = false
        // Letters, digits, and symbols sit on the lighter key; named keys are darker.
        val action = modifier || key.label.length > 1
        val view = TextView(context).apply {
            // The key by its guest code, as the controller mapping names keys.
            testId("keyboard.key", key.code.toString())
            gravity = Gravity.CENTER
            setTextColor(keyText())
            setAutoSizeTextTypeUniformWithConfiguration(9, 16, 1, TypedValue.COMPLEX_UNIT_SP)
            isClickable = true
            setOnTouchListener { _, event ->
                if (mapping?.active == true) {
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) mapping.tap(keyList(key))
                    return@setOnTouchListener true
                }
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        if (holdToLatch) {
                            unlockedOnPress = key.code in latched
                            if (unlockedOnPress) toggleModifier(key.code)
                            press(key)
                        } else if (modifier) input.tap(key.code)
                        else press(key)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (holdToLatch) {
                            if (event.actionMasked == MotionEvent.ACTION_UP && !unlockedOnPress &&
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
                if (mapping?.active == true) mapping.tap(keyList(key))
                else if (modifier && !holdToLatch) input.tap(key.code)
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
        keyActions[view] = action
        keyViews.add(key to view)
        return view
    }

    private fun press(key: KeyboardKey) {
        highlightUntil[key.code] = Lit(SystemClock.uptimeMillis() + 90, key.chordShift || shiftHeld())
        input.hold("touch-key:${key.code}", if (key.chordShift) listOf(layout.chordShiftCode, key.code)
            else listOf(key.code))
    }

    private fun release(key: KeyboardKey) {
        input.release("touch-key:${key.code}")
        val delay = (highlightUntil[key.code]?.until ?: 0L) - SystemClock.uptimeMillis()
        if (delay > 0) handler.postDelayed({ updateHighlights() }, delay)
    }

    private fun shiftHeld() = layout.shiftCodes.any { it in input.pressedKeys() }

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
        val locks = input.lockedKeys()
        val caps = layout.capsCode?.let { it in locks } == true
        val kana = layout.kanaCode?.let { it in locks } == true
        val numLockOff = layout.numLockCode?.let { it !in locks } == true
        for ((key, view) in keyViews) {
            val letter = key.label.length == 1 && key.label[0] in 'a'..'z'
            view.text = when {
                numLockOff && key.numLockOff != null -> key.numLockOff
                kana && key.kana != null -> if (shifted) key.kanaShifted ?: key.kana else key.kana
                letter && (shifted xor caps) -> key.label.uppercase()
                key.shifted != null && shifted -> key.shifted
                else -> key.label
            }
            view.isActivated = key.code in latched || key.code in locks
        }
        updateHighlights()
    }

    private fun updateHighlights() {
        val pressed = input.pressedKeys()
        val now = SystemClock.uptimeMillis()
        val shifted = layout.shiftCodes.any { it in pressed }
        for (scan in pressed - lastPressed)
            highlightUntil[scan] = Lit(now + 90, shifted)
        for (scan in lastPressed - pressed) {
            val delay = (highlightUntil[scan]?.until ?: 0L) - now
            if (delay > 0) handler.postDelayed({ updateHighlights() }, delay)
        }
        lastPressed = pressed
        highlightUntil.entries.removeAll { it.value.until <= now }
        for ((key, view) in keyViews) {
            val lit = highlightUntil[key.code]
            val shiftedPress = when {
                key.code in pressed -> shifted
                lit != null -> lit.shifted
                else -> { view.isPressed = false; continue }
            }
            // Where one scan code is two keys here, as 5 and %, light only the one Shift chose.
            view.isPressed = when {
                key.chordShift -> shiftedPress
                key.code in chordCodes -> !shiftedPress
                else -> true
            }
        }
    }

    private fun showPressed(binding: ControllerBinding) {
        val spec = inputs.spec ?: return
        if (visibility != View.VISIBLE) return
        pressedLabel.text = spec.describe(binding)
        pressedLabel.visibility = View.VISIBLE
        handler.removeCallbacks(hidePressedLabel)
        handler.postDelayed(hidePressedLabel, 900)
    }

    private fun keyBackground(action: Boolean = false) = PanelKeyStyle.keyBackground(context, action)
    private fun mappingBackground(fill: Int, stroke: Int?) = PanelKeyStyle.mappingBackground(context, fill, stroke)
    private fun keyText() = PanelKeyStyle.keyText()

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

}
