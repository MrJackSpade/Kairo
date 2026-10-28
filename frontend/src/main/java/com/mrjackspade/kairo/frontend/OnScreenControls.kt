package com.mrjackspade.kairo.frontend


import android.app.Activity
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.StringRes
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
    private enum class LayoutOrientation(@StringRes val label: Int, val preference: String) {
        PORTRAIT(R.string.kairo_orientation_portrait, "onscreen_layout_portrait_v2"),
        LANDSCAPE(R.string.kairo_orientation_landscape, "onscreen_layout_landscape_v2")
    }

    /** [label] is the control's legend, as a controller prints it; [group] is a key into [GROUPS]. */
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
    private var arrangement: FrameLayout? = null
    private var requestedVisible = false
    /** Pages to refresh when the orientation or controller layout changes under them. */
    var pages: PageStack? = null
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

    private var mode = OnScreenMode.load(preferences)
    private var controllerConnected = ControllerCapabilities.connected().isNotEmpty()
    private val enabled get() = mode.shown(controllerConnected)

    /** The host's input device listener calls this so Auto follows controllers live. */
    fun devicesChanged() {
        controllerConnected = ControllerCapabilities.connected().isNotEmpty()
        refreshVisibility(requestedVisible)
    }

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
        val show = playing && enabled && arrangement == null && guestKeyboard?.visibility != View.VISIBLE
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

    /** Controls › ON-SCREEN CONTROLS, for the current orientation and controller layout. */
    fun group() = RowGroup(str(R.string.kairo_onscreen_group), listOf(
        SettingsRow.choice("osc", str(R.string.kairo_onscreen_controls), ChoiceSpec(str(R.string.kairo_onscreen_controls), { GameSource.ALL_GAMES }, {
            listOf(ChoiceGroup(null, OnScreenMode.entries.map {
                ChoiceOption(it.name, UiText.res(it.label), UiText.res(it.description))
            }))
        }, { mode.name }, select = { value ->
            mode = OnScreenMode.valueOf(value)
            mode.save(preferences)
            refreshVisibility(requestedVisible)
        })),
        SettingsRow.choice("dpad-style", str(R.string.kairo_onscreen_dpad_style), ChoiceSpec(str(R.string.kairo_onscreen_dpad_style), { GameSource.ALL_GAMES }, {
            listOf(ChoiceGroup(null, listOf(
                ChoiceOption("arrows", UiText.res(R.string.kairo_onscreen_dpad_arrows), UiText.res(R.string.kairo_onscreen_dpad_arrows_help)),
                ChoiceOption("pad", UiText.res(R.string.kairo_onscreen_dpad_pad), UiText.res(R.string.kairo_onscreen_dpad_pad_help)))))
        }, { if (eightWayDpad) "pad" else "arrows" }, select = { eightWayDpad = it == "pad" })),
        SettingsRow.nav("visible", str(R.string.kairo_onscreen_visible), value = { str(R.string.kairo_onscreen_shown_count, visibleCount()) }) {
            pages?.push(visiblePage())
        },
        SettingsRow.nav("arrange", str(R.string.kairo_onscreen_arrange)) { pages?.push(arrangePage()) },
        SettingsRow.action("reset-positions", str(R.string.kairo_onscreen_reset_positions),
            confirm = Confirm(str(R.string.kairo_onscreen_reset_positions_confirm), str(R.string.kairo_reset)) {
                specs.forEach { spec ->
                    val default = defaultPosition(spec, orientation)
                    states.getValue(spec.id).apply { x = default.first; y = default.second }
                }
                saveLayout()
                positionAll()
                Ui.message(activity, str(R.string.kairo_onscreen_positions_reset))
            })),
        str(R.string.kairo_onscreen_foot))

    private fun relevant(spec: Spec) = when {
        spec.id in DPAD -> !eightWayDpad
        spec.id == PAD8 -> eightWayDpad
        spec.group == "STICKS" -> profile == ControllerLayout.WITH_STICKS
        else -> true
    }

    private fun visibleCount() = specs.count { relevant(it) && states.getValue(it.id).shown }

    private fun visiblePage() = GroupsPage(str(R.string.kairo_onscreen_visible),
        { str(R.string.kairo_onscreen_visible_scope, str(orientation.label), str(profile.label)) }, {
            specs.filter(::relevant).groupBy { it.group }.map { (group, items) ->
                RowGroup(str(GROUPS.getValue(group)), items.map { spec ->
                    SettingsRow.switch("v:${spec.id}", controlName(spec.id), { states.getValue(spec.id).shown }) { value ->
                        states.getValue(spec.id).shown = value
                        saveLayout()
                        positionAll()
                    }
                })
            } + RowGroup(null, listOf(SettingsRow.action("v:reset", str(R.string.kairo_onscreen_reset_visible),
                confirm = Confirm(str(R.string.kairo_onscreen_reset_visible_confirm), str(R.string.kairo_reset)) {
                    specs.forEach { spec -> states.getValue(spec.id).shown = defaultShown(spec, profile) }
                    saveLayout()
                    positionAll()
                    Ui.message(activity, str(R.string.kairo_onscreen_visible_reset))
                })))
        })

    /** Drag the shown controls into place over the game; Done returns to Controls. */
    private fun arrangePage() = ViewPage({ page ->
        val layer = FrameLayout(activity).apply {
            clipChildren = false
            clipToPadding = false
            setBackgroundColor(0x33000000)
            isClickable = true
        }
        arrangement = layer
        for (spec in specs.filter { states.getValue(it.id).shown }) layer.addView(controlView(spec, true))
        val bar = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(6), 0)
            setBackgroundColor(0xee10151d.toInt())
        }
        bar.addView(Ui.text(activity, str(R.string.kairo_onscreen_drag), Ui.BODY), LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(Ui.text(activity, str(R.string.kairo_done), Ui.TITLE, Ui.ACCENT, bold = true).apply {
            gravity = Gravity.CENTER
            background = Ui.focusable(activity, Color.TRANSPARENT, null)
            isClickable = true
            setOnClickListener { page.stack.pop() }
        }, LinearLayout.LayoutParams(dp(76), dp(44)))
        layer.addView(bar, FrameLayout.LayoutParams(-1, dp(50), Gravity.TOP))
        refreshVisibility(requestedVisible)
        layer.post(::positionAll)
        layer
    }, { page ->
        val layer = arrangement ?: return@ViewPage emptyList()
        val done = (layer.getChildAt(layer.childCount - 1) as LinearLayout).getChildAt(1)
        listOf(NavItem("done", done, 0, 0.5f, { done.performClick() }))
    }) {
        arrangement = null
        refreshVisibility(requestedVisible)
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
            // The control by its id ("a", "start", "ls"); the arranger's copy is named apart.
            TestAttributes.mark(this, if (editing) "touch.arrange" else "touch.control", spec.id)
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
            pages?.refresh()
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
        "up" -> str(R.string.kairo_up); "down" -> str(R.string.kairo_down)
        "left" -> str(R.string.kairo_left); "right" -> str(R.string.kairo_right)
        "ls" -> str(R.string.kairo_onscreen_left_stick); "rs" -> str(R.string.kairo_onscreen_right_stick)
        "start" -> str(R.string.kairo_control_start); "select" -> str(R.string.kairo_control_select)
        "menu" -> str(R.string.kairo_control_menu)
        PAD8 -> str(R.string.kairo_onscreen_dpad_pad)
        else -> id.uppercase()
    }

    private fun str(id: Int, vararg args: Any): String = activity.getString(id, *args)

    private fun buttonBackground(editing: Boolean) = GradientDrawable().apply {
        setColor(if (editing) 0xdd304e63.toInt() else 0xb5253344.toInt())
        // Round, so the face-button diamond never overlaps at its standard spacing.
        cornerRadius = dp(27).toFloat()
        setStroke(dp(1), 0xccffffff.toInt())
    }

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).roundToInt()

    private companion object {
        val DPAD = listOf("up", "down", "left", "right")
        /** Each control group's heading on Visible buttons, by the key its specs name. */
        val GROUPS = mapOf("D-PAD" to R.string.kairo_control_group_dpad, "FACE BUTTONS" to R.string.kairo_control_group_face,
            "SHOULDERS" to R.string.kairo_control_group_shoulders, "SYSTEM" to R.string.kairo_control_group_system,
            "STICKS" to R.string.kairo_onscreen_group_sticks)
        const val PAD8 = "pad8"
    }
}
