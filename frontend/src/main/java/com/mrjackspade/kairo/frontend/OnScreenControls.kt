package com.mrjackspade.kairo.frontend


import android.app.Activity
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Touch buttons use the same virtual controls and per-game bindings as a physical controller. */
class OnScreenControls(
    private val activity: Activity,
    private val root: FrameLayout,
    private val mapper: GamepadMapper,
    private val preferences: SharedPreferences,
    private val onVisibilityChanged: () -> Unit,
    private val controllerLayout: () -> ControllerLayout
) {
    private enum class LayoutOrientation(val label: String, val preference: String) {
        PORTRAIT("Portrait", "onscreen_layout_portrait_v2"),
        LANDSCAPE("Landscape", "onscreen_layout_landscape_v2")
    }

    private data class Spec(val id: String, val label: String, val group: String,
                            val x: Float, val y: Float, val shown: Boolean)
    private data class State(var shown: Boolean, var x: Float, var y: Float)

    private val specs = listOf(
        Spec("up", "↑", "D-PAD", .16f, .67f, true),
        Spec("down", "↓", "D-PAD", .16f, .83f, true),
        Spec("left", "←", "D-PAD", .09f, .75f, true),
        Spec("right", "→", "D-PAD", .23f, .75f, true),
        Spec(PAD8, "", "D-PAD", .16f, .75f, false),
        Spec("a", "A", "FACE BUTTONS", .88f, .82f, true),
        Spec("b", "B", "FACE BUTTONS", .94f, .74f, true),
        Spec("x", "X", "FACE BUTTONS", .82f, .74f, true),
        Spec("y", "Y", "FACE BUTTONS", .88f, .66f, true),
        Spec("l1", "L1", "SHOULDERS", .08f, .39f, false),
        Spec("r1", "R1", "SHOULDERS", .92f, .39f, false),
        Spec("l2", "L2", "SHOULDERS", .08f, .51f, false),
        Spec("r2", "R2", "SHOULDERS", .92f, .51f, false),
        Spec("start", "Start", "SYSTEM", .57f, .91f, true),
        Spec("select", "Select", "SYSTEM", .43f, .91f, true),
        Spec("menu", "Menu", "SYSTEM", .50f, .18f, true),
        Spec("ls", "L", "STICKS", .30f, .80f, false),
        Spec("rs", "R", "STICKS", .70f, .80f, false)
    )
    private val portraitPositions = mapOf(
        "up" to (.18f to .62f), "down" to (.18f to .78f),
        "left" to (.09f to .70f), "right" to (.27f to .70f), PAD8 to (.18f to .70f),
        "a" to (.84f to .77f), "b" to (.92f to .69f),
        "x" to (.76f to .69f), "y" to (.84f to .61f),
        "l1" to (.12f to .44f), "r1" to (.88f to .44f),
        "l2" to (.12f to .53f), "r2" to (.88f to .53f),
        "start" to (.59f to .91f), "select" to (.41f to .91f),
        "menu" to (.50f to .54f),
        "ls" to (.23f to .85f), "rs" to (.77f to .85f)
    )
    private val layouts = ControllerLayout.entries.associateWith {
        LayoutOrientation.entries.associateWith { LinkedHashMap<String, State>() }
    }
    private var profile = controllerLayout()
    private var orientation = if (activity.resources.configuration.orientation ==
        Configuration.ORIENTATION_PORTRAIT) LayoutOrientation.PORTRAIT
        else LayoutOrientation.LANDSCAPE
    private val states: LinkedHashMap<String, State> get() = layouts.getValue(profile).getValue(orientation)
    private val buttons = LinkedHashMap<String, View>()
    private val heldPointers = HashMap<String, MutableSet<Int>>()
    /** Directions each D-pad pointer holds, for the four arrows and for the 8-way pad. A finger
     * that lands on any arrow steers all four. */
    private val dpadPointers = HashMap<Int, Set<String>>()
    private val padPointers = HashMap<Int, Set<String>>()
    private val overlay = FrameLayout(activity).apply {
        visibility = View.GONE
        isClickable = false
        isFocusable = false
        setMotionEventSplittingEnabled(true)
        clipChildren = false
        clipToPadding = false
    }
    private var page: LinearLayout? = null
    private var settingsBody: LinearLayout? = null
    private var settingsScroll: ScrollView? = null
    private var settingsTitle: TextView? = null
    private var arrangement: FrameLayout? = null
    private var returnToParent: ((Boolean) -> Unit)? = null
    private var requestedVisible = false
    private var guestKeyboard: GuestKeyboardPanel? = null
    private val keyboardVisibilityListener: () -> Unit = {
        refreshVisibility(requestedVisible)
    }

    /** Keep touch controls below the guest keyboard in both host apps. */
    fun bindGuestKeyboard(panel: GuestKeyboardPanel?) {
        guestKeyboard?.removeVisibilityListener(keyboardVisibilityListener)
        guestKeyboard = panel
        panel?.addVisibilityListener(keyboardVisibilityListener)
        refreshVisibility(requestedVisible)
    }

    private var enabled = preferences.getBoolean("onscreen_enabled",
        InputDevice.getDeviceIds().none { id ->
            InputDevice.getDevice(id)?.let { device ->
                !device.isVirtual && (device.supportsSource(InputDevice.SOURCE_GAMEPAD) ||
                    device.supportsSource(InputDevice.SOURCE_JOYSTICK))
            } == true
        })

    val isOpen: Boolean get() = page != null

    /** Whether the round 8-way pad replaces the four arrows, in both orientations. */
    var eightWayDpad: Boolean
        get() = states.getValue(PAD8).shown
        set(value) {
            for (layout in LayoutOrientation.entries) {
                val controls = layouts.getValue(profile).getValue(layout)
                controls.getValue(PAD8).shown = value
                DPAD.forEach { controls.getValue(it).shown = !value }
                saveLayout(layout)
            }
            positionAll()
            if (isOpen) renderSettings()
        }

    init {
        loadLayouts()
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        specs.forEach { spec ->
            buttons[spec.id] = controlView(spec, false).also {
                overlay.addView(it)
            }
        }
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> positionAll() }
        root.post(::positionAll)
    }

    fun refreshVisibility(playing: Boolean) {
        requestedVisible = playing
        val show = playing && enabled && !isOpen && guestKeyboard?.visibility != View.VISIBLE
        if (!show) {
            mapper.releaseOnScreen()
            heldPointers.values.forEach(MutableSet<Int>::clear)
            dpadPointers.clear()
            padPointers.clear()
            (buttons[PAD8] as? DpadView)?.held = emptySet()
            buttons.values.filterIsInstance<TouchStickView>().forEach { it.reset() }
            buttons.values.forEach { it.isPressed = false; it.alpha = if (it is TouchStickView) 1f else .72f }
        }
        overlay.visibility = if (show) View.VISIBLE else View.GONE
        if (show) positionAll()
    }

    fun hitTest(x: Float, y: Float): Boolean {
        if (overlay.visibility != View.VISIBLE) return false
        return buttons.values.any { view ->
            view.visibility == View.VISIBLE && x >= view.left && x < view.right &&
                y >= view.top && y < view.bottom
        }
    }

    fun show(onReturn: (Boolean) -> Unit = {}) {
        if (isOpen) return
        returnToParent = onReturn
        positionAll()
        val settings = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            isFocusableInTouchMode = true
            elevation = dp(20).toFloat()
        }
        page = settings
        root.addView(settings, FrameLayout.LayoutParams(-1, -1))
        val bar = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Ui.BG)
            setPadding(dp(12), dp(7), dp(12), dp(7))
        }
        bar.addView(Ui.iconButton(activity, R.drawable.ic_back, "Back") { back() },
            LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(8) })
        settingsTitle = TextView(activity).apply {
            text = "Controls · ${orientation.label}"
            textSize = Ui.TITLE
            setTextColor(Ui.TEXT)
            gravity = Gravity.CENTER_VERTICAL
        }
        bar.addView(settingsTitle, LinearLayout.LayoutParams(0, dp(48), 1f))
        settings.addView(bar)
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(20))
        }
        settingsBody = body
        val scroll = ScrollView(activity).apply { addView(body) }
        settingsScroll = scroll
        settings.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        renderSettings()
        settings.requestFocus()
        onVisibilityChanged()
    }

    fun back() {
        if (arrangement != null) {
            root.removeView(arrangement)
            arrangement = null
            page?.visibility = View.VISIBLE
            renderSettings()
            return
        }
        close(returning = true)
    }

    fun close(returning: Boolean = false) {
        val returnAction = returnToParent
        returnToParent = null
        arrangement?.let(root::removeView)
        arrangement = null
        page?.let(root::removeView)
        page = null
        settingsBody = null
        settingsScroll = null
        settingsTitle = null
        refreshVisibility(requestedVisible)
        onVisibilityChanged()
        returnAction?.invoke(returning)
    }

    fun handleKey(event: KeyEvent): Boolean {
        if (!isOpen) return false
        if (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_ESCAPE ||
            event.keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) back()
            return true
        }
        return false
    }

    private fun renderSettings() {
        val body = settingsBody ?: return
        val scrollY = settingsScroll?.scrollY ?: 0
        body.removeAllViews()
        note(body, "Edit ${orientation.label.lowercase()} buttons here. Positions and visible buttons are saved separately for portrait and landscape. Each control follows the Global or This game controller mapping. Layouts are also saved separately for With Sticks and Without Sticks.")
        row(body, "Show on-screen controls", if (enabled) "On" else "Off") {
            enabled = !enabled
            preferences.edit().putBoolean("onscreen_enabled", enabled).apply()
            renderSettings()
        }
        row(body, "Arrange controls", "Drag buttons into place") { showArrangement() }
        for (group in specs.map { it.group }.distinct()) {
            section(body, group)
            if (group == "D-PAD") {
                row(body, "D-pad style", if (eightWayDpad) "8-way pad" else "4 buttons") {
                    eightWayDpad = !eightWayDpad
                }
            }
            specs.filter { it.group == group }.forEach { spec ->
                val state = states.getValue(spec.id)
                row(body, controlName(spec.id), if (state.shown) "On" else "Off") {
                    state.shown = !state.shown
                    saveLayout()
                    positionAll()
                    renderSettings()
                }
            }
        }
        section(body, "RESET")
        row(body, "Reset positions", "Restore the standard layout") {
            specs.forEach { spec ->
                val default = defaultPosition(spec, orientation)
                states.getValue(spec.id).apply { x = default.first; y = default.second }
            }
            saveLayout()
            positionAll()
            renderSettings()
        }
        row(body, "Reset visible buttons", "Restore the standard selection") {
            specs.forEach { spec -> states.getValue(spec.id).shown = defaultShown(spec, profile) }
            saveLayout()
            positionAll()
            renderSettings()
        }
        settingsScroll?.post { settingsScroll?.scrollTo(0, scrollY) }
    }

    private fun showArrangement() {
        page?.visibility = View.GONE
        val layer = FrameLayout(activity).apply {
            clipChildren = false
            clipToPadding = false
            setBackgroundColor(0x33000000)
            isClickable = true
            elevation = dp(22).toFloat()
        }
        arrangement = layer
        root.addView(layer, FrameLayout.LayoutParams(-1, -1))
        for (spec in specs.filter { states.getValue(it.id).shown }) {
            layer.addView(controlView(spec, true))
        }
        val bar = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            setBackgroundColor(0xee10151d.toInt())
        }
        bar.addView(TextView(activity).apply {
            text = "Drag controls to reposition"
            textSize = Ui.BODY
            setTextColor(Ui.TEXT)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(TextView(activity).apply {
            text = "Done"
            textSize = Ui.TITLE
            setTextColor(Ui.ACCENT)
            gravity = Gravity.CENTER
            setOnClickListener { back() }
        }, LinearLayout.LayoutParams(dp(76), -1))
        layer.addView(bar, FrameLayout.LayoutParams(-1, dp(50), Gravity.TOP))
        layer.post(::positionAll)
    }

    private fun controlView(spec: Spec, editing: Boolean): View {
        val held = if (editing) HashSet<Int>() else heldPointers.getOrPut(spec.id) { HashSet() }
        val view: View = when (spec.id) {
            PAD8 -> DpadView(activity)
            "ls", "rs" -> TouchStickView(activity) { x, y ->
                if (!editing) mapper.moveVirtualStick(spec.id, x, y)
            }
            else -> TextView(activity).apply {
                text = spec.label
                textSize = if (spec.label.length > 2) 11f else 20f
                setTextColor(Ui.TEXT)
                gravity = Gravity.CENTER
                background = buttonBackground(editing)
            }
        }
        return view.apply {
            tag = spec.id
            contentDescription = controlName(spec.id)
            isClickable = true
            if (editing) {
                var rawX = 0f
                var rawY = 0f
                var startX = 0f
                var startY = 0f
                setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            rawX = event.rawX; rawY = event.rawY
                            startX = states.getValue(spec.id).x * root.width
                            startY = states.getValue(spec.id).y * root.height
                            view.alpha = 1f
                        }
                        MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                            val size = controlSize(spec.id)
                            val x = (startX + event.rawX - rawX)
                                .coerceIn(size / 2f, (root.width - size / 2f).coerceAtLeast(size / 2f))
                            val y = (startY + event.rawY - rawY)
                                .coerceIn(dp(50) + size / 2f,
                                    (root.height - size / 2f).coerceAtLeast(dp(50) + size / 2f))
                            states.getValue(spec.id).apply {
                                this.x = x / root.width.coerceAtLeast(1)
                                this.y = y / root.height.coerceAtLeast(1)
                            }
                            position(view, states.getValue(spec.id), root.width, root.height)
                            if (event.actionMasked == MotionEvent.ACTION_UP) {
                                view.alpha = if (view is TouchStickView) 1f else .85f
                                saveLayout()
                            }
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            states.getValue(spec.id).apply {
                                x = startX / root.width.coerceAtLeast(1)
                                y = startY / root.height.coerceAtLeast(1)
                            }
                            position(view, states.getValue(spec.id), root.width, root.height)
                            view.alpha = if (view is TouchStickView) 1f else .85f
                        }
                    }
                    true
                }
            } else if (spec.id in DPAD || spec.id == PAD8) setOnTouchListener { view, event ->
                dpadTouch(view, event, eightWay = spec.id == PAD8)
                true
            } else if (spec.id !in setOf("ls", "rs")) setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                        val pointer = event.getPointerId(event.actionIndex)
                        if (held.add(pointer)) {
                            mapper.pressVirtual(spec.id, "onscreen:${spec.id}:$pointer")
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        }
                        view.isPressed = true
                        view.alpha = 1f
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                        val pointer = event.getPointerId(event.actionIndex)
                        if (held.remove(pointer)) mapper.releaseVirtual(
                            "onscreen:${spec.id}:$pointer")
                        view.isPressed = held.isNotEmpty()
                        view.alpha = if (held.isEmpty()) .72f else 1f
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        held.toList().forEach { mapper.releaseVirtual("onscreen:${spec.id}:$it") }
                        held.clear()
                        view.isPressed = false
                        view.alpha = .72f
                    }
                }
                true
            }
            alpha = if (spec.id in setOf("ls", "rs")) 1f else if (editing) .85f else .72f
        }
    }

    /**
     * The four arrows steer together in four directions from the center of the visible arrows.
     * The optional round pad reads eight directions from its own center, so diagonals press two.
     */
    private fun dpadTouch(view: View, event: MotionEvent, eightWay: Boolean) {
        val pointers = if (eightWay) padPointers else dpadPointers
        val owner = if (eightWay) "onscreen:pad8" else "onscreen:dpad"
        fun directionsAt(index: Int): Set<String> {
            val cx: Float
            val cy: Float
            if (eightWay) {
                cx = view.width / 2f
                cy = view.height / 2f
            } else {
                val arrows = DPAD.mapNotNull { id -> buttons[id]?.takeIf { it.visibility == View.VISIBLE } }
                if (arrows.isEmpty()) return emptySet()
                cx = arrows.map { it.left + it.width / 2f }.average().toFloat() - view.left
                cy = arrows.map { it.top + it.height / 2f }.average().toFloat() - view.top
            }
            val dx = event.getX(index) - cx
            val dy = event.getY(index) - cy
            val deadZone = if (eightWay) view.width * 0.12f else dp(10).toFloat()
            if (hypot(dx, dy) < deadZone) return emptySet()
            val degrees = Math.toDegrees(atan2(dy, dx).toDouble()) + 360
            return if (eightWay) DpadView.SECTORS[((degrees + 22.5) % 360 / 45).toInt()]
                else listOf(setOf("right"), setOf("down"), setOf("left"), setOf("up"))[((degrees + 45) % 360 / 90).toInt()]
        }
        fun update(pointer: Int, next: Set<String>) {
            val prior = pointers[pointer] ?: emptySet()
            (prior - next).forEach { mapper.releaseVirtual("$owner:$it:$pointer") }
            (next - prior).forEach { mapper.pressVirtual(it, "$owner:$it:$pointer") }
            if (next.isEmpty()) pointers.remove(pointer) else pointers[pointer] = next
            if ((next - prior).isNotEmpty()) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN ->
                update(event.getPointerId(event.actionIndex), directionsAt(event.actionIndex))
            MotionEvent.ACTION_MOVE -> for (index in 0 until event.pointerCount)
                update(event.getPointerId(index), directionsAt(index))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP ->
                update(event.getPointerId(event.actionIndex), emptySet())
            MotionEvent.ACTION_CANCEL -> pointers.keys.toList().forEach { update(it, emptySet()) }
        }
        val held = pointers.values.flatten().toSet()
        if (eightWay) {
            (view as? DpadView)?.held = held
            view.alpha = if (held.isEmpty()) .72f else 1f
        } else DPAD.forEach { id ->
            buttons[id]?.let {
                it.isPressed = id in held
                it.alpha = if (id in held) 1f else .72f
            }
        }
    }

    private fun positionAll() {
        if (root.width <= 0 || root.height <= 0) return
        val next = if (root.height > root.width) LayoutOrientation.PORTRAIT
            else LayoutOrientation.LANDSCAPE
        val nextProfile = controllerLayout()
        if (next != orientation || nextProfile != profile) {
            orientation = next
            profile = nextProfile
            mapper.releaseOnScreen()
            heldPointers.values.forEach(MutableSet<Int>::clear)
            dpadPointers.clear()
            padPointers.clear()
            (buttons[PAD8] as? DpadView)?.held = emptySet()
            buttons.values.filterIsInstance<TouchStickView>().forEach { it.reset() }
            buttons.values.forEach { it.isPressed = false; it.alpha = if (it is TouchStickView) 1f else .72f }
            settingsTitle?.text = "Controls · ${orientation.label}"
            if (isOpen) renderSettings()
            rebuildArrangementButtons()
        }
        buttons.forEach { (id, view) ->
            val state = states.getValue(id)
            view.visibility = if (state.shown) View.VISIBLE else View.GONE
            position(view, state, root.width, root.height)
        }
        arrangement?.let { layer ->
            for (index in 0 until layer.childCount) {
                val view = layer.getChildAt(index)
                val id = view.tag as? String ?: continue
                position(view, states.getValue(id), root.width, root.height)
            }
        }
    }

    private fun rebuildArrangementButtons() {
        val layer = arrangement ?: return
        val bar = layer.getChildAt(layer.childCount - 1)
        layer.removeAllViews()
        specs.filter { states.getValue(it.id).shown }.forEach { spec ->
            layer.addView(controlView(spec, true))
        }
        layer.addView(bar)
    }

    private fun position(view: View, state: State, width: Int, height: Int) {
        val size = controlSize(view.tag as String)
        val left = (state.x * width - size / 2f).roundToInt().coerceIn(0, (width - size).coerceAtLeast(0))
        val top = (state.y * height - size / 2f).roundToInt().coerceIn(0, (height - size).coerceAtLeast(0))
        val params = view.layoutParams as? FrameLayout.LayoutParams ?: FrameLayout.LayoutParams(size, size)
        if (params.leftMargin == left && params.topMargin == top && params.width == size) return
        params.width = size; params.height = size
        params.gravity = Gravity.TOP or Gravity.START
        params.leftMargin = left; params.topMargin = top
        view.layoutParams = params
    }

    private fun layoutPreference(layout: LayoutOrientation, profile: ControllerLayout) =
        "${layout.preference}_${profile.key}"

    private fun loadLayouts() {
        for (profile in ControllerLayout.entries) for (layout in LayoutOrientation.entries) {
            val saved = preferences.getString(layoutPreference(layout, profile), null)
            val legacy = preferences.getString(layout.preference, null)
                ?: if (layout == orientation) preferences.getString("onscreen_layout_v1", null) else null
            val stored = try { JSONObject(saved ?: legacy ?: "{}") } catch (_: Exception) { JSONObject() }
            val controls = stored.optJSONObject("controls")
            for (spec in specs) {
                val item = controls?.optJSONObject(spec.id)
                val default = defaultPosition(spec, layout, profile)
                val oldDefault = if (layout == LayoutOrientation.PORTRAIT) portraitPositions.getValue(spec.id)
                    else spec.x to spec.y
                var x = item?.optDouble("x", default.first.toDouble())?.toFloat() ?: default.first
                var y = item?.optDouble("y", default.second.toDouble())?.toFloat() ?: default.second
                // Carry customized legacy positions forward, but give untouched defaults the new profile layout.
                if (saved == null && kotlin.math.abs(x - oldDefault.first) < .001f &&
                    kotlin.math.abs(y - oldDefault.second) < .001f) {
                    x = default.first; y = default.second
                }
                layouts.getValue(profile).getValue(layout)[spec.id] = State(
                    item?.optBoolean("shown", defaultShown(spec, profile)) ?: defaultShown(spec, profile),
                    if (x.isFinite()) x.coerceIn(0f, 1f) else default.first,
                    if (y.isFinite()) y.coerceIn(0f, 1f) else default.second)
            }
        }
    }

    private fun defaultShown(spec: Spec, profile: ControllerLayout) =
        if (spec.id == "ls" || spec.id == "rs") profile == ControllerLayout.WITH_STICKS else spec.shown

    private fun defaultPosition(spec: Spec, layout: LayoutOrientation,
                                profile: ControllerLayout = this.profile): Pair<Float, Float> {
        val original = if (layout == LayoutOrientation.PORTRAIT) portraitPositions.getValue(spec.id)
            else spec.x to spec.y
        if (profile != ControllerLayout.WITH_STICKS || spec.id in setOf("ls", "rs")) return original
        return when (spec.group) {
            "D-PAD", "FACE BUTTONS" -> original.first to (original.second -
                if (layout == LayoutOrientation.PORTRAIT) .12f else .27f)
            "SHOULDERS" -> original.first to (original.second - .22f)
            else -> original
        }
    }

    private fun controlSize(id: String) = when (id) {
        PAD8 -> dp(148)
        "ls", "rs" -> minOf(dp(120), (minOf(root.width, root.height) * .28f).roundToInt().coerceAtLeast(dp(72)))
        else -> dp(54)
    }

    private fun saveLayout(layout: LayoutOrientation = orientation) {
        val controls = JSONObject()
        specs.forEach { spec ->
            val state = layouts.getValue(profile).getValue(layout).getValue(spec.id)
            controls.put(spec.id, JSONObject().put("shown", state.shown)
                .put("x", state.x.toDouble()).put("y", state.y.toDouble()))
        }
        preferences.edit().putString(layoutPreference(layout, profile),
            JSONObject().put("version", 3).put("controls", controls).toString()).apply()
    }

    private fun controlName(id: String) = when (id) {
        "up" -> "D-pad up"; "down" -> "D-pad down"
        "left" -> "D-pad left"; "right" -> "D-pad right"
        "ls" -> "Left stick"; "rs" -> "Right stick"
        PAD8 -> "8-way D-pad"
        else -> id.uppercase()
    }

    private fun buttonBackground(editing: Boolean) = GradientDrawable().apply {
        setColor(if (editing) 0xdd304e63.toInt() else 0xb5253344.toInt())
        // Round, so the face-button diamond never overlaps at its standard spacing.
        cornerRadius = dp(27).toFloat()
        setStroke(dp(1), 0xccffffff.toInt())
    }

    private fun section(parent: LinearLayout, title: String) {
        parent.addView(Ui.sectionLabel(activity, title))
    }

    private fun note(parent: LinearLayout, message: String) {
        parent.addView(Ui.text(activity, message, Ui.SECONDARY, Ui.TEXT_MUTED).apply {
            setPadding(dp(12), dp(8), dp(12), dp(8))
        })
    }

    private fun row(parent: LinearLayout, title: String, value: String, action: () -> Unit) {
        val line = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(4), dp(14), dp(4))
            background = Ui.rowBackground(activity, Ui.SURFACE)
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
            contentDescription = "$title. $value"
        }
        line.addView(Ui.text(activity, title, Ui.BODY), LinearLayout.LayoutParams(0, -2, 1f))
        line.addView(Ui.text(activity, value, Ui.SECONDARY, Ui.ACCENT_SOFT).apply { gravity = Gravity.END },
            LinearLayout.LayoutParams(-2, -2))
        parent.addView(line, LinearLayout.LayoutParams(-1, dp(52)).apply {
            bottomMargin = dp(4)
        })
    }

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).roundToInt()

    private companion object {
        val DPAD = listOf("up", "down", "left", "right")
        const val PAD8 = "pad8"
    }
}
