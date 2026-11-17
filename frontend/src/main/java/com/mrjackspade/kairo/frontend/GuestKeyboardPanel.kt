package com.mrjackspade.kairo.frontend

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
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
import android.view.ViewGroup
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
    layout: GuestKeyboardLayout,
    private val onClose: () -> Unit,
    private val showClose: Boolean = true,
    private val onSwap: (() -> Unit)? = null,
    /** Whether the touchpad tab is offered, given a mouse in [inputs]. */
    touchpadTab: Boolean = true,
    mouseReferenceSize: () -> Pair<Int, Int> = { 640 to 400 },
    private val onVisibilityChanged: (() -> Unit)? = null
) : LinearLayout(context) {
    /** The page numbers of the touchpad and Joystick tabs, which also key their tabs for the UI tests. */
    companion object {
        const val TOUCHPAD_PAGE = -1
        const val JOYSTICK_PAGE = -2
        /** The smallest size, in sp, a key's legend or a tab's name shrinks to. */
        const val MIN_LEGEND_SP = 9
    }

    private val input = inputs.keys
    private val mapping = inputs.mapping
    /** The keys as the running machine labels them ([setLayout]). */
    private var layout = layout
    private var brandText: PixelTextView? = null

    private val handler = Handler(Looper.getMainLooper())
    private val latched = mutableSetOf<Int>()
    private val keyViews = mutableListOf<Pair<KeyboardKey, TextView>>()
    private val pageViews = mutableMapOf<Int, View>()
    private val touchpad = inputs.mouse?.takeIf { touchpadTab }?.let { SecondaryTouchpadView(context, it, mouseReferenceSize) }
    /** Split, the left half's touchpad; both halves move the same mouse. */
    private val leftTouchpad = inputs.mouse?.takeIf { touchpadTab }?.let { SecondaryTouchpadView(context, it, mouseReferenceSize) }
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
    /** The page's place: docked, the whole page; split, the right half's keys or the touchpad or joystick. */
    private val body = FrameLayout(context)
    /** Split, the left half's keys. */
    private val leftBody = FrameLayout(context)
    /** The bodies, arranged by [arrange]. */
    private val content = LinearLayout(context)
    /** The machine's keys, touchpad and joystick, with the mapping prompt over them: the real keyboard's layout in every language; the tab bar mirrors (Kairo#129). */
    private val stage = FrameLayout(context).apply { layoutDirection = View.LAYOUT_DIRECTION_LTR }
    /** The page tabs, in the header docked and atop the left half split. */
    private val tabBar = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
    /** The Joystick and touchpad tabs: at the end of the tabs docked, and atop the right half split. */
    private val iconTabs = mutableListOf<View>()
    private var mappingButton: View? = null
    private var closeButton: TextView? = null
    /** The game screen's brand row with Swap, on the second screen's panel only. */
    private var brandRow: LinearLayout? = null
    /** The halves' widths at the screen's sides, or null for rows along its bottom ([setSplit]). */
    private var sides: KeyboardSides? = null
    private val split get() = sides != null
    /** Split, the columns holding the halves, sized to [sides]. */
    private var leftColumn: View? = null
    private var rightColumn: View? = null
    private val keyActions = mutableMapOf<TextView, Boolean>()
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
        visibility = View.GONE
        // A touch surface: no key in it may take focus, or a controller press after a touch
        // would focus and light the first one (the mapping button) and could press it.
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        isFocusable = false

        // Mapping is a keyboard control, not an input page: it sits with Swap, or with Close without a second screen.
        mappingButton = mapping?.let { mode ->
            ImageView(context).apply {
                setImageResource(R.drawable.ic_gamepad)
                imageTintList = keyText()
                setPadding(dp(2), dp(8), dp(2), dp(8))
                contentDescription = context.getString(R.string.kairo_keyboard_mapping)
                testId("keyboard.mapping")
                background = keyBackground(action = true)
                onPress(this) { mode.toggle() }
            }
        }
        if (onSwap != null) {
            val brand = LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPaddingRelative(dp(14), dp(4), dp(8), dp(4))
            }
            brandText = PixelTextView(context).apply { text = layout.brand; this.brand = true }
            brand.addView(brandText, LayoutParams(0, -2, 1f))
            brand.addView(TextView(context).apply {
                text = "\u2191\u2193"
                contentDescription = context.getString(R.string.kairo_keyboard_swap)
                testId("keyboard.swap")
                gravity = Gravity.CENTER
                textSize = 22f
                setTextColor(keyText())
                background = keyBackground(action = true)
                setOnClickListener { onSwap.invoke() }
            }, LayoutParams(dp(54), dp(40)))
            brandRow = brand
        }

        for ((target, keyboardPage) in layout.pages.withIndex()) {
            val tab = TextView(context).apply {
                text = keyboardPage.label
                // Legends such as ?123 are the keyboard's, not the language's: a right-to-left
                // bar would move the leading "?" to the end.
                textDirection = View.TEXT_DIRECTION_LTR
                gravity = Gravity.CENTER
                // One line, shrunk to fit the split keyboard's narrower tab bar.
                maxLines = 1
                setAutoSizeTextTypeUniformWithConfiguration(MIN_LEGEND_SP, Ui.SECONDARY.toInt(), 1, TypedValue.COMPLEX_UNIT_SP)
                setTextColor(keyText())
                attachPageSwitch(this, target)
            }
            tab.background = keyBackground(action = true)
            pageViews[target] = tab
            tabBar.addView(tab, tabParams())
        }
        if (joystickPage != null) {
            val tab = ImageView(context).apply {
                setImageResource(R.drawable.ic_joystick)
                imageTintList = keyText()
                setPadding(dp(2), dp(7), dp(2), dp(7))
                contentDescription = context.getString(R.string.kairo_joystick)
                background = keyBackground(action = true)
                attachPageSwitch(this, JOYSTICK_PAGE)
            }
            pageViews[JOYSTICK_PAGE] = tab
            iconTabs.add(tab)
        }
        if (touchpad != null) {
            val tab = ImageView(context).apply {
                setImageResource(R.drawable.ic_mouse)
                imageTintList = keyText()
                setPadding(dp(2), dp(7), dp(2), dp(7))
                contentDescription = context.getString(R.string.kairo_keyboard_touchpad_mode)
                background = keyBackground(action = true)
                attachPageSwitch(this, TOUCHPAD_PAGE)
            }
            pageViews[TOUCHPAD_PAGE] = tab
            iconTabs.add(tab)
        }
        if (showClose) closeButton = TextView(context).apply {
            contentDescription = context.getString(R.string.kairo_keyboard_close)
            testId("keyboard.close")
            gravity = Gravity.CENTER
            textSize = 14f
            setTextColor(Ui.ACCENT_SOFT)
            setOnClickListener { onClose() }
        }
        stage.addView(content, FrameLayout.LayoutParams(-1, -1))
        stage.addView(pressedLabel, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dp(6)
        })
        if (mapping != null) mappingPrompt = mappingPrompt().also {
            stage.addView(it, FrameLayout.LayoutParams(-1, -1))
        }
        arrange()
        showPage(0)
    }

    /**
     * The game screen's keyboard as halves [value] wide at the screen's sides, with the game
     * between them, or with null as rows along its bottom. The second screen's keyboard does not split.
     */
    fun setSplit(value: KeyboardSides?) {
        require(onSwap == null || value == null) { "The second screen's keyboard does not split" }
        if (value == sides) return
        val rearrange = (value == null) != (sides == null)
        sides = value
        if (rearrange) {
            arrange()
            showPage(page)
        } else sizeColumns()
    }

    /**
     * Whether halves [value] wide are usable: every key on every page shows its whole legend in
     * every state (Shift, kana, Num Lock) at the smallest size legends shrink to, and every page
     * tab its name. Halves too narrow for that leave the keyboard docked.
     */
    fun splitFits(value: KeyboardSides): Boolean {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = MIN_LEGEND_SP * resources.displayMetrics.scaledDensity }
        val gap = dp(2)
        for (keyboardPage in layout.pages) {
            val (left, right) = KeyGrid.split(KeyGrid.place(keyboardPage.rows))
            for ((cells, width) in listOf(left to value.left, right to value.right)) for (cell in cells) {
                val room = (cell.right - cell.left) * width - 2 * gap
                if (legends(cell.key).any { paint.measureText(it) > room }) return false
            }
        }
        // The page tabs share the left half's header (arrange).
        val tabRoom = (value.left - 2 * dp(4)) / layout.pages.size.toFloat() - 2 * dp(2)
        return layout.pages.none { paint.measureText(it.label) > tabRoom }
    }

    /** Every legend [key] can show. */
    private fun legends(key: KeyboardKey) =
        listOfNotNull(key.label, key.label.uppercase(), key.shifted, key.kana, key.kanaShifted, key.numLockOff)

    /** Gives the split columns the widths of [sides]. */
    private fun sizeColumns() {
        val size = sides ?: return
        leftColumn?.layoutParams = LayoutParams(size.left, -1)
        rightColumn?.layoutParams = LayoutParams(size.right, -1)
    }

    /** A tab's or header control's place in its row: equal shares, a little apart. */
    private fun tabParams() = LayoutParams(0, dp(34), 1f).apply { setMargins(dp(2), 0, dp(2), 0) }

    /**
     * Places the tabs, the controls and the bodies for [split]: docked, a header of every tab,
     * Mapping and Close over the page; split, a column at each side of the screen, the page tabs
     * atop the left and the Joystick and touchpad tabs, Mapping and a compact Close atop the
     * right, and between them nothing, so touches there reach the game.
     */
    private fun arrange() {
        removeAllViews()
        content.removeAllViews()
        leftColumn = null
        rightColumn = null
        (listOfNotNull(tabBar, mappingButton, closeButton, brandRow, leftBody, body) + iconTabs)
            .forEach { (it.parent as? ViewGroup)?.removeView(it) }
        val mappingWidth = dp(54)
        if (!split) {
            setBackgroundColor(Ui.SURFACE)
            elevation = dp(14).toFloat()
            content.orientation = VERTICAL
            content.addView(body, LayoutParams(-1, -1))
            iconTabs.forEach { tabBar.addView(it, tabParams()) }
            closeButton?.text = context.getString(R.string.kairo_keyboard_close)
            brandRow?.let { brand ->
                // The Mapping button sits with Swap on the second screen.
                mappingButton?.let { brand.addView(it, 1, LayoutParams(mappingWidth, dp(40)).apply { marginEnd = dp(8) }) }
                addView(brand, LayoutParams(-1, dp(48)))
            }
            val header = LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(6), dp(3), dp(6), dp(3))
            }
            tabBar.setPadding(0, 0, 0, 0)
            header.addView(tabBar, LayoutParams(0, -1, 1f))
            if (brandRow == null) mappingButton?.let {
                header.addView(it, LayoutParams(mappingWidth, dp(34)).apply { marginStart = dp(10) })
            }
            closeButton?.let { header.addView(it, LayoutParams(dp(76), dp(34))) }
            addView(header, LayoutParams(-1, dp(42)))
            addView(stage, if (onSwap == null) LayoutParams(-1, 0, 1f) else LayoutParams(-1, dp(320)))
            return
        }
        setBackgroundColor(Color.TRANSPARENT)
        elevation = 0f
        content.orientation = HORIZONTAL
        fun column(top: View, page: View) = LinearLayout(context).apply {
            orientation = VERTICAL
            setBackgroundColor(Ui.SURFACE)
            elevation = dp(14).toFloat()
            addView(top, LayoutParams(-1, dp(42)))
            addView(page, LayoutParams(-1, 0, 1f))
        }
        val controls = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(3), dp(4), dp(3))
            iconTabs.forEach { addView(it, tabParams()) }
            mappingButton?.let { addView(it, tabParams()) }
            closeButton?.let {
                // Its name stays its description; the half has room for its mark only.
                it.text = "×"
                addView(it, tabParams())
            }
        }
        tabBar.setPadding(dp(4), dp(3), dp(4), dp(3))
        leftColumn = column(tabBar, leftBody).also { content.addView(it) }
        content.addView(View(context), LayoutParams(0, -1, 1f))
        rightColumn = column(controls, body).also { content.addView(it) }
        sizeColumns()
        addView(stage, LayoutParams(-1, -1))
    }

    private fun mappingPrompt(): View = LinearLayout(context).apply {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        visibility = View.GONE
        isClickable = true
        setBackgroundColor(Ui.SCRIM)
        setPadding(dp(24), dp(12), dp(24), dp(12))
        addView(Ui.text(context, context.getString(R.string.kairo_keyboard_mapping), Ui.TITLE, bold = true).apply { gravity = Gravity.CENTER })
        addView(Ui.text(context, context.getString(R.string.kairo_keyboard_mapping_help), Ui.BODY, Ui.TEXT_BODY).apply {
            gravity = Gravity.CENTER
        }, LayoutParams(-2, -2).apply { setMargins(0, dp(8), 0, dp(14)) })
        val ok = Ui.primaryButton(context, context.getString(R.string.kairo_ok)) { visibility = View.GONE }
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
        mappingButton?.isActivated = mode.active
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

    private fun keyList(key: KeyboardKey) = layout.keysFor(key, input.lockedKeys())

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
        leftTouchpad?.close()
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
        if (page == TOUCHPAD_PAGE) {
            touchpad?.close()
            leftTouchpad?.close()
        }
        if (page == JOYSTICK_PAGE) joystickPage?.releaseAll()
        input.releasePrefix("touch-key:")
        if (page != target) {
            for (shift in layout.shiftCodes) {
                if (latched.remove(shift)) input.release("touch-mod:$shift")
            }
        }
        page = target
        updateContentSize()
        body.removeAllViews()
        leftBody.removeAllViews()
        keyViews.clear()
        keyActions.clear()
        if (target == TOUCHPAD_PAGE || target == JOYSTICK_PAGE) {
            joystickPage?.setSplit(split)
            if (split) {
                // Each half holds part of the page: a touchpad each, or the joystick's directions and its buttons.
                val (left, right) = if (target == TOUCHPAD_PAGE) leftTouchpad to touchpad
                    else joystickPage to joystickPage?.buttonsHalf
                left?.let { leftBody.addView(it, FrameLayout.LayoutParams(-1, -1)) }
                right?.let { body.addView(it, FrameLayout.LayoutParams(-1, -1)) }
            } else (if (target == TOUCHPAD_PAGE) touchpad else joystickPage)?.let {
                body.addView(it, FrameLayout.LayoutParams(-1, -1))
            }
            pageViews.forEach { (name, view) -> view.isActivated = name == page }
            return
        }
        fun grid(cells: List<KeyGrid.Cell>) = KeyGrid(context, dp(2), layout.pages[page].rows.size).apply {
            for (cell in cells) add(keyView(cell.key), cell)
        }
        val cells = KeyGrid.place(layout.pages[page].rows)
        if (split) {
            val (left, right) = KeyGrid.split(cells)
            leftBody.addView(grid(left), FrameLayout.LayoutParams(-1, -1))
            body.addView(grid(right), FrameLayout.LayoutParams(-1, -1))
        } else body.addView(grid(cells), FrameLayout.LayoutParams(-1, -1))
        chordCodes = keyViews.filter { it.first.chordShift }.map { it.first.code }.toSet()
        pageViews.forEach { (name, view) -> view.isActivated = name == page }
        updateLegends()
        if (mapping?.active == true) updateMappingKeys()
    }

    /**
     * The keys of another machine of the same app (a Japanese MSX's JIS keyboard), with the same pages:
     * the page shown is drawn again with the new legends.
     */
    fun setLayout(value: GuestKeyboardLayout) {
        require(value.pages.map { it.label } == layout.pages.map { it.label }) {
            "A keyboard's layouts must have the same pages"
        }
        if (value == layout) return
        for (shift in layout.shiftCodes) {
            if (latched.remove(shift)) input.release("touch-mod:$shift")
        }
        latched.clear()
        layout = value
        brandText?.text = value.brand
        if (page >= 0) showPage(page)
    }

    /** The running machine's own pads on the Joystick tab (GuestJoystickPage.setPorts). */
    fun setJoystickPorts(value: List<JoystickPort>) {
        joystickPage?.setPorts(value)
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
            // One line: a narrow key, as in a split half, shrinks its legend rather than breaking it mid-word.
            maxLines = 1
            setAutoSizeTextTypeUniformWithConfiguration(MIN_LEGEND_SP, 16, 1, TypedValue.COMPLEX_UNIT_SP)
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
        highlightUntil[key.code] = Lit(SystemClock.uptimeMillis() + 90, keyList(key).size > 1 || shiftHeld())
        // A Shift chord's Shift reaches the guest before its key (Kairo#191).
        input.holdShifted("touch-key:${key.code}", keyList(key), layout.shiftCodes)
    }

    private fun release(key: KeyboardKey) {
        input.releaseShifted("touch-key:${key.code}")
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
        for ((key, view) in keyViews) {
            // Only a legend that changed is set: setText lays the key out again (fitting its
            // auto-sized text) even for the same text, and this runs for every key the guest gets,
            // so typing on a hardware keyboard blocked the main thread until Android dropped keys
            // (KairoX86#226).
            val legend = layout.legend(key, shifted, locks)
            if (view.text.toString() != legend) view.text = legend
            view.isActivated = key.code in latched || key.code in locks
        }
        updateHighlights()
    }

    private fun updateHighlights() {
        val held = input.pressedKeys()
        val now = SystemClock.uptimeMillis()
        // A chord's Shift picks the shifted legend but does not light Shift itself (Kairo64#3).
        val shifted = layout.shiftCodes.any { it in held }
        val pressed = GuestKeyboardHighlights.litKeys(held, input.holds(), layout.shiftCodes, layout.modifiers)
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
        pressedLabel.text = spec.describe(context, binding)
        pressedLabel.visibility = View.VISIBLE
        handler.removeCallbacks(hidePressedLabel)
        handler.postDelayed(hidePressedLabel, 900)
    }

    private fun keyBackground(action: Boolean = false) = PanelKeyStyle.keyBackground(context, action)
    private fun mappingBackground(fill: Int, stroke: Int?) = PanelKeyStyle.mappingBackground(context, fill, stroke)
    private fun keyText() = PanelKeyStyle.keyText()

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

}
