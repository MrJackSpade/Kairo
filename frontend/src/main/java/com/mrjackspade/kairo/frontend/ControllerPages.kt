package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.roundToInt

/** What a guest machine accepts from a controller: its keys, joystick, and app actions. */
data class ControllerGuestSpec(
    val name: String,
    val keyCodes: List<Int>,
    val keyLabel: (Int) -> String,
    val modifierCodes: Set<Int>,
    val joystickControls: List<Pair<String, String>>,
    val joystickHelp: String,
    val actions: List<Pair<String, String>>,
    /** Keys are picked on the guest keyboard layout; codes it lacks go on an Other tab. */
    val keyboard: GuestKeyboardLayout
) {
    companion object {
        /** The app actions every Kairo app offers a controller. */
        val STANDARD_ACTIONS = listOf("menu" to "Open menu", "pause" to "Pause or resume",
            "fastForward" to "Fast forward while held", "restart" to "Restart", "exit" to "Exit")
    }
}

/** The virtual controls in the one order every controller page uses. */
internal object VirtualControls {
    class Control(val id: String, val short: String, val full: String)
    class Group(val label: String, val stick: Boolean, val controls: List<Control>)

    val groups = listOf(
        Group("D-PAD", false, listOf(Control("up", "Up", "D-pad up"), Control("down", "Down", "D-pad down"),
            Control("left", "Left", "D-pad left"), Control("right", "Right", "D-pad right"))),
        Group("FACE BUTTONS", false, listOf(Control("a", "A", "A button"), Control("b", "B", "B button"),
            Control("x", "X", "X button"), Control("y", "Y", "Y button"))),
        Group("SHOULDERS", false, listOf(Control("l1", "L1", "L1 button"), Control("r1", "R1", "R1 button"),
            Control("l2", "L2", "L2 button"), Control("r2", "R2", "R2 button"))),
        Group("SYSTEM", false, listOf(Control("start", "Start", "Start button"),
            Control("select", "Select", "Select button"), Control("menu", "Menu", "Menu button"))),
        Group("LEFT STICK", true, listOf(Control("lsup", "Up", "Left stick up"),
            Control("lsdown", "Down", "Left stick down"), Control("lsleft", "Left", "Left stick left"),
            Control("lsright", "Right", "Left stick right"))),
        Group("RIGHT STICK", true, listOf(Control("rsup", "Up", "Right stick up"),
            Control("rsdown", "Down", "Right stick down"), Control("rsleft", "Left", "Right stick left"),
            Control("rsright", "Right", "Right stick right"))))

    fun full(id: String) = groups.flatMap { it.controls }.firstOrNull { it.id == id }?.full ?: id
}

/** A physical input the Choose input page offers, grouped by where it is on a controller. */
internal class InputSource(val group: String, val label: String, val name: String, val input: String)

internal object InputSources {
    fun common(): List<InputSource> {
        val buttons = listOf("A" to KeyEvent.KEYCODE_BUTTON_A, "B" to KeyEvent.KEYCODE_BUTTON_B,
            "X" to KeyEvent.KEYCODE_BUTTON_X, "Y" to KeyEvent.KEYCODE_BUTTON_Y,
            "L1" to KeyEvent.KEYCODE_BUTTON_L1, "R1" to KeyEvent.KEYCODE_BUTTON_R1,
            "L2" to KeyEvent.KEYCODE_BUTTON_L2, "R2" to KeyEvent.KEYCODE_BUTTON_R2
        ).map { (label, code) -> InputSource("BUTTONS", label, "Button $label", "button:$code") } + listOf(
            InputSource("BUTTONS", "Start", "Start", "button:${KeyEvent.KEYCODE_BUTTON_START}"),
            InputSource("BUTTONS", "Select", "Select", "button:${KeyEvent.KEYCODE_BUTTON_SELECT}"),
            InputSource("BUTTONS", "Mode", "Mode", "button:${KeyEvent.KEYCODE_BUTTON_MODE}"),
            InputSource("BUTTONS", "Left stick press", "Left stick press", "button:${KeyEvent.KEYCODE_BUTTON_THUMBL}"),
            InputSource("BUTTONS", "Right stick press", "Right stick press", "button:${KeyEvent.KEYCODE_BUTTON_THUMBR}"))
        val dpad = listOf("Up" to KeyEvent.KEYCODE_DPAD_UP, "Down" to KeyEvent.KEYCODE_DPAD_DOWN,
            "Left" to KeyEvent.KEYCODE_DPAD_LEFT, "Right" to KeyEvent.KEYCODE_DPAD_RIGHT
        ).map { (label, code) -> InputSource("D-PAD BUTTONS", label, "D-pad ${label.lowercase()}", "button:$code") }
        fun axes(group: String, name: String, x: Int, y: Int, kind: String = "axis") = listOf(
            InputSource(group, "Up", "$name up", "$kind:$y:-"), InputSource(group, "Down", "$name down", "$kind:$y:+"),
            InputSource(group, "Left", "$name left", "$kind:$x:-"), InputSource(group, "Right", "$name right", "$kind:$x:+"))
        return buttons + dpad +
            axes("LEFT STICK", "Left stick", MotionEvent.AXIS_X, MotionEvent.AXIS_Y) +
            axes("RIGHT STICK", "Right stick", MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ) +
            listOf(InputSource("TRIGGERS", "Left trigger", "Left trigger", "axis:${MotionEvent.AXIS_LTRIGGER}:+"),
                InputSource("TRIGGERS", "Right trigger", "Right trigger", "axis:${MotionEvent.AXIS_RTRIGGER}:+")) +
            axes("D-PAD HAT", "Hat", MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y, "hat")
    }

    /** Axes a connected controller has beyond the common ones, grouped under its name. */
    fun extra(): List<InputSource> {
        val known = common().map { it.input }.toSet()
        return InputDevice.getDeviceIds().toList().mapNotNull(InputDevice::getDevice)
            .filter { it.supportsSource(InputDevice.SOURCE_JOYSTICK) }.flatMap { device ->
                device.motionRanges.map { it.axis }.distinct()
                    .flatMap { axis -> listOf(axisInput(axis, -1f), axisInput(axis, 1f)) }
                    .filter { it !in known }
                    .map { InputSource(device.name.uppercase(), name(it), name(it), it) }
            }.distinctBy { it.input }
    }

    fun axisInput(axis: Int, value: Float): String {
        val kind = if (axis == MotionEvent.AXIS_HAT_X || axis == MotionEvent.AXIS_HAT_Y) "hat" else "axis"
        return kind + ":" + axis + ":" + if (value > 0) "+" else "-"
    }

    /** A readable name for any input code, such as Button A or Axis GAS +. */
    fun name(input: String): String {
        common().firstOrNull { it.input == input }?.let { return it.name }
        val parts = input.split(':')
        if (parts.size == 2 && parts[0] == "button") {
            val code = parts[1].toIntOrNull() ?: return input
            val key = KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
            return if (key.startsWith("BUTTON_")) "Button " + key.removePrefix("BUTTON_")
                else key.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
        }
        if (parts.size == 3) {
            val axis = parts[1].toIntOrNull() ?: return input
            return (if (parts[0] == "hat") "Hat " else "Axis ") +
                MotionEvent.axisToString(axis).removePrefix("AXIS_") + " " + parts[2]
        }
        return input
    }
}

/**
 * Controls, Button mapping, the button target page, Controller inputs, and their pickers.
 * Button mapping has one page per scope: all games from Controls, a game from Game settings.
 */
class ControllerPages<Game : Any>(
    private val activity: Activity,
    private val pages: PageStack,
    private val load: (Game?) -> List<ControllerBinding>,
    private val save: (Game?, List<ControllerBinding>) -> Unit,
    private val reset: (Game?) -> Unit,
    private val globalCustom: () -> Boolean,
    private val loadPhysical: () -> List<PhysicalControllerBinding>,
    private val savePhysical: (List<PhysicalControllerBinding>) -> Unit,
    private val resetPhysical: () -> Unit,
    private val physicalCustom: () -> Boolean,
    private val getDeadZone: () -> Float,
    private val setDeadZone: (Float) -> Unit,
    private val guest: ControllerGuestSpec,
    private val validateBindings: (List<ControllerBinding>) -> Unit,
    private val configuration: ControllerConfiguration?,
    private val onConfigurationChanged: () -> Unit,
    private val mappingStatus: (Game?) -> String?,
    private val gameTitle: (Game) -> String,
    private val onScreen: OnScreenControls,
    private val touch: TouchSettingsCoordinator<Game>,
    /** Called after any mapping change, so the running game uses it at once. */
    private val changed: () -> Unit = {}
) {
    private fun message(text: String) = Ui.message(activity, text)

    private fun sticks() = configuration?.layout != ControllerLayout.WITHOUT_STICKS

    /** Controls, for all games. In a game, the footnote points to that game's own mapping. */
    fun controlsPage(running: Game?) = GroupsPage("Controls", { GameSource.ALL_GAMES }, {
        listOf(
            RowGroup("CONTROLLER", listOfNotNull(
                configuration?.let { layoutRow(it) },
                SettingsRow.nav("mapping", "Button mapping",
                    value = { if (globalCustom()) "Custom" else "Default" },
                    tone = { if (globalCustom()) ValueTone.CUSTOM else ValueTone.SUMMARY }) {
                    pages.push(mappingPage(null))
                },
                SettingsRow.nav("inputs", "Controller inputs",
                    value = { if (physicalCustom()) "Custom" else "Standard" },
                    tone = { if (physicalCustom()) ValueTone.CUSTOM else ValueTone.SUMMARY }) {
                    pages.push(inputsPage())
                },
                SettingsRow.slider("dead-zone", "Stick dead zone", { "${(getDeadZone() * 100).roundToInt()}%" },
                    SliderSpec(10, 90, 1, 5, { (getDeadZone() * 100).roundToInt() }) {
                        setDeadZone(it / 100f)
                    })), running?.let { "${gameTitle(it)} can have its own button mapping. Change it in Game settings." }),
            touch.globalGroup(),
            onScreen.group())
    })

    private fun layoutRow(config: ControllerConfiguration): SettingsRow {
        val spec = ChoiceSpec("Controller layout", { GameSource.ALL_GAMES }, {
            val detected = ControllerCapabilities.detect(ControllerCapabilities.connected())
            listOf(ChoiceGroup(null, listOfNotNull(
                detected.device?.let {
                    ChoiceOption(ControllerConfiguration.Choice.AUTO.name, "Auto",
                        "Detected: ${detected.layout.label.lowercase()}")
                },
                ChoiceOption(ControllerConfiguration.Choice.WITH_STICKS.name, "With sticks"),
                ChoiceOption(ControllerConfiguration.Choice.WITHOUT_STICKS.name, "Without sticks"))))
        }, { config.choice.name }, select = { picked ->
            config.select(ControllerConfiguration.Choice.valueOf(picked))
            onConfigurationChanged()
            changed()
        })
        return SettingsRow.choice("layout", "Controller layout", spec, value = { config.label })
    }

    /** Game settings › Button mapping for one game. */
    fun gameRow(game: Game, enabled: () -> Boolean, source: () -> String) =
        SettingsRow.nav("game-mapping", "Button mapping", value = source,
            tone = { if (source() == GameSource.CUSTOM) ValueTone.CUSTOM else ValueTone.SUMMARY },
            enabled = enabled, disabledReason = { "Hash this game before editing its controls" }) {
            pages.push(mappingPage(game))
        }

    private fun scope(game: Game?) = game?.let(gameTitle) ?: GameSource.ALL_GAMES

    fun mappingPage(game: Game?) = GroupsPage("Button mapping", { scope(game) }, {
        val bindings = load(game).associateBy { it.input }
        val groups = VirtualControls.groups.filter { sticks() || !it.stick }.mapIndexed { index, group ->
            RowGroup(group.label, group.controls.map { control ->
                val bound = bindings["virtual:${control.id}"]
                val inherited = if (control.id.startsWith("ls")) bindings["virtual:" + control.id.removePrefix("ls")] else null
                SettingsRow.nav("m:${control.id}", control.short,
                    value = { bound?.let(::targetLabel) ?: inherited?.let { targetLabel(it) + " (follows D-pad)" } ?: "Unassigned" },
                    tone = { when {
                        bound != null -> ValueTone.CURRENT
                        inherited != null -> ValueTone.SUMMARY
                        else -> ValueTone.EMPTY
                    } }) {
                    pages.push(TargetPage(game, "virtual:${control.id}", control.full))
                }
            }, if (index == 0) mappingStatus(game) else null)
        }.toMutableList()
        val legacy = bindings.values.filter { !it.input.startsWith("virtual:") }
        if (legacy.isNotEmpty()) groups += RowGroup("LEGACY DIRECT INPUTS", legacy.map { binding ->
            SettingsRow.nav("m:${binding.input}", InputSources.name(binding.input), value = { targetLabel(binding) },
                tone = { ValueTone.CURRENT }) {
                pages.push(TargetPage(game, binding.input, InputSources.name(binding.input)))
            }
        }, "These saved assignments still work. Reset this mapping to use virtual controls.")
        groups += RowGroup(null, listOf(SettingsRow.action("m:reset", "Reset to defaults",
            confirm = Confirm(if (game == null) "Restore the built-in mapping for all games?"
                else "Remove this game’s changes and use its catalog or global mapping?", "Reset") {
                change("Mapping reset") { reset(game) }
            })))
        groups
    })

    /** Controller inputs: which physical input drives each virtual control, for all games. */
    fun inputsPage() = GroupsPage("Controller inputs", { GameSource.ALL_GAMES }, {
        val bindings = loadPhysical().groupBy { it.control }
        val connected = InputDevice.getDeviceIds().toList().mapNotNull(InputDevice::getDevice)
            .filter { it.supportsSource(InputDevice.SOURCE_GAMEPAD) || it.supportsSource(InputDevice.SOURCE_JOYSTICK) }
            .joinToString { it.name }
        listOf(RowGroup("CONNECTED", listOf(SettingsRow.info("connected", "Controller") {
            connected.ifEmpty { "None connected" }
        }))) + VirtualControls.groups.map { group ->
            RowGroup(group.label, group.controls.map { control ->
                val sources = bindings[control.id].orEmpty()
                SettingsRow.action("p:${control.id}", control.short,
                    value = { when (sources.size) {
                        0 -> "Unassigned"
                        1 -> InputSources.name(sources[0].input)
                        else -> InputSources.name(sources[0].input) + " + ${sources.size - 1} more"
                    } },
                    tone = { if (sources.isEmpty()) ValueTone.EMPTY else ValueTone.CURRENT },
                    tray = { listenTray(control.id, sources.isNotEmpty()) })
            })
        } + RowGroup(null, listOf(
            SettingsRow.action("p:add", "Add alternate input", tray = { listenTray(null, false) }),
            SettingsRow.action("p:reset", "Reset to standard layout",
                confirm = Confirm("Restore the standard controller layout?", "Reset") {
                    change("Controller inputs reset") { resetPhysical() }
                })))
    })

    /** Listening starts when the tray opens; Enter code and Clear sit in the same tray. */
    private fun listenTray(control: String?, assigned: Boolean): TraySpec = TraySpec(
        if (control == null) "Listening. Press the extra button, then choose the control it acts as."
        else "Listening. Press a button, or move a stick, trigger, or D-pad.",
        listOfNotNull(
            TrayButton("Cancel"),
            if (control != null && assigned) TrayButton("Clear") {
                change(null) { savePhysical(loadPhysical().filterNot { it.control == control }) }
            } else null,
            TrayButton("Choose input") { pages.push(chooseInputPage(control)) },
            TrayButton("Enter code", keepOpen = true) { pages.replaceTray(codeTray(control)) }),
        listening = true, focus = 0, capture = PhysicalInputCapture { input ->
            pages.closeTray()
            assign(control, input)
        })

    private fun codeTray(control: String?) = TraySpec(
        "Android key and axis codes, such as button:96, axis:0:+, or hat:15:-.",
        listOf(TrayButton("Cancel"), TrayButton("Assign", ButtonTone.PRIMARY, keepOpen = true) { text ->
            val input = text.trim().lowercase()
            try {
                PhysicalControllerBindings.toJson(listOf(PhysicalControllerBinding(input, "a")))
            } catch (_: Exception) {
                message("Invalid input code")
                return@TrayButton
            }
            pages.closeTray()
            assign(control, input)
        }), TrayInput("button:96"))

    private fun assign(control: String?, input: String) {
        if (control == null) {
            pages.push(actsAsPage(input))
            return
        }
        change(null) {
            val updated = loadPhysical().filterNot { it.input == input || it.control == control } +
                PhysicalControllerBinding(input, control)
            PhysicalControllerBindings.toJson(updated)
            savePhysical(updated)
        }
        pages.flash("p:$control")
    }

    private fun chooseInputPage(control: String?): GroupsPage {
        val current = control?.let { id -> loadPhysical().firstOrNull { it.control == id }?.input }
        val sources = InputSources.common() + InputSources.extra()
        val spec = ChoiceSpec("Choose input", { control?.let(VirtualControls::full) ?: "Alternate input" },
            { sources.groupBy { it.group }.map { (group, items) ->
                ChoiceGroup(group, items.map { ChoiceOption(it.input, it.label, it.input) })
            } }, { current }, { input ->
                if (control == null) {
                    pages.pop()
                    pages.push(actsAsPage(input))
                } else assign(control, input)
            }, stay = control == null, cycles = false)
        return ChoicePage(spec, control?.let { "p:$it" })
    }

    private fun actsAsPage(input: String) = ChoicePage(ChoiceSpec("Acts as", { InputSources.name(input) }, {
        VirtualControls.groups.map { group ->
            ChoiceGroup(group.label, group.controls.map { ChoiceOption(it.id, it.full) })
        }
    }, { loadPhysical().firstOrNull { it.input == input }?.control }, { control ->
        val name = InputSources.name(input)
        change("Added $name as ${VirtualControls.full(control)}") {
            val updated = loadPhysical().filterNot { it.input == input } + PhysicalControllerBinding(input, control)
            PhysicalControllerBindings.toJson(updated)
            savePhysical(updated)
        }
    }, cycles = false))

    private fun change(done: String?, action: () -> Unit) {
        try {
            action()
            changed()
            done?.let(::message)
        } catch (error: Exception) { message(error.message ?: "Could not save mapping") }
        pages.refresh()
    }

    private fun targetLabel(binding: ControllerBinding): String = when {
        binding.cycleKeys.isNotEmpty() -> {
            val direction = if (binding.input == ControllerKeyCycles.pair(binding.input)?.first) "previous" else "next"
            "Cycle $direction " + binding.cycleKeys.joinToString(" → ") { guest.keyLabel(it) }
        }
        binding.mouse != null -> "Mouse " + when (binding.mouse) {
            "leftButton" -> "left button"
            "rightButton" -> "right button"
            else -> binding.mouse.removePrefix("move").lowercase()
        } + if (binding.mouse.startsWith("move")) java.lang.String.format(
            java.util.Locale.ROOT, " · %.1f×", binding.mouseSpeed) else ""
        binding.joystick != null -> "Joystick " +
            (guest.joystickControls.firstOrNull { it.first == binding.joystick }?.second?.lowercase()
                ?: binding.joystick)
        binding.action != null -> guest.actions.firstOrNull { it.first == binding.action }?.second
            ?: binding.action.replaceFirstChar(Char::uppercase)
        else -> binding.keys.joinToString(" + ") { guest.keyLabel(it) }
    }

    private enum class Tab(val label: String) { KEYS("Keys"), CYCLE("Key cycle"), JOYSTICK("Joystick"),
        MOUSE("Mouse"), ACTION("Action") }

    private class Key(val code: Int, val label: String, val weight: Float)

    /** The pages of the key picker: the guest keyboard, then codes it does not show. */
    private val keyTabs: List<Pair<String, List<List<Key>>>> by lazy {
        val plain = guest.keyboard.pages.flatMap { page -> page.rows.flatten().filter { !it.chordShift }.map { it.code } }.toSet()
        val tabs = guest.keyboard.pages.map { page ->
            page.pickerLabel to page.rows.map { row ->
                row.filter { !it.chordShift || it.code !in plain }
                    .filter { it.code in guest.keyCodes }
                    .map { key ->
                        val label = if (key.chordShift) guest.keyLabel(key.code)
                            else if (key.label.length == 1 && key.label[0].isLetter()) key.label.uppercase() else key.label
                        Key(key.code, label, key.width)
                    }
            }.filter { it.isNotEmpty() }
        }
        val shown = tabs.flatMap { it.second.flatten() }.map { it.code }.toSet()
        val other = guest.keyCodes.filter { it !in shown }.map { code ->
            Key(code, guest.keyLabel(code).removePrefix("Scan "), 1f)
        }
        (if (other.isEmpty()) tabs else tabs + ("Other" to other.chunked(8))).filter { it.second.isNotEmpty() }
    }

    /**
     * What one control does: a tab for keys, key cycle, joystick, mouse, or an app action.
     * It is the one page that holds a draft, with Clear and Save in its footer.
     */
    private inner class TargetPage(private val game: Game?, private val input: String,
                                   private val title: String) : RowsPage() {
        private val existing = load(game).firstOrNull { it.input == input }
        private val cycleCapable = input in ControllerKeyCycles.inputs
        private val tabs = Tab.entries.filter { it != Tab.CYCLE || cycleCapable }
        private var tab = when {
            existing == null -> Tab.KEYS
            existing.cycleKeys.isNotEmpty() && cycleCapable -> Tab.CYCLE
            existing.joystick != null -> Tab.JOYSTICK
            existing.mouse != null -> Tab.MOUSE
            existing.action != null -> Tab.ACTION
            else -> Tab.KEYS
        }
        private var keyTab = 0
        private val keys = ArrayList<Int>(existing?.keys.orEmpty())
        private val cycle = ArrayList<Int>(existing?.cycleKeys?.takeIf { it.isNotEmpty() } ?: run {
            val pair = ControllerKeyCycles.pair(input)
            load(game).firstOrNull { pair != null && (it.input == pair.first || it.input == pair.second) &&
                it.cycleKeys.isNotEmpty() }?.cycleKeys.orEmpty()
        })
        private var joystick = existing?.joystick
        private var mouse = existing?.mouse
        private var speed = ((existing?.mouseSpeed ?: 1f) * 10).roundToInt().coerceIn(1, 200)
        private var action = existing?.action

        private lateinit var scrollView: ScrollView
        private lateinit var tabRow: LinearLayout
        private lateinit var summary: TextView
        private lateinit var hint: TextView
        private lateinit var keyArea: LinearLayout
        private lateinit var footer: Footer
        private val tabViews = ArrayList<Pair<Tab, TextView>>()
        private val subTabViews = ArrayList<TextView>()
        private inner class KeyView(val key: Key, val row: Int, val x: Float) {
            val label: TextView = Ui.text(context, key.label,
                if (key.label.length > 3) Ui.LABEL else if (key.label.length > 1) Ui.SECONDARY else Ui.BODY).apply {
                gravity = Gravity.CENTER
                maxLines = 1
            }
            val number: TextView = Ui.text(context, "", 10f, Ui.ON_ACCENT, bold = true)
            val view = FrameLayout(context).apply {
                isClickable = true
                contentDescription = guest.keyLabel(key.code) + " 0x" + key.code.toString(16).padStart(2, '0')
                testId("target.key", key.code.toString())
                setOnClickListener { toggle(key.code) }
                addView(label, FrameLayout.LayoutParams(-1, -1))
                addView(number, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
                    topMargin = dp(2)
                    marginEnd = dp(4)
                })
            }
        }
        private val keyViews = ArrayList<KeyView>()
        override val scroll: ScrollView get() = scrollView
        override val testName: String get() = "Button target"

        private val mouseOptions = listOf("moveUp" to "Move up", "moveDown" to "Move down",
            "moveLeft" to "Move left", "moveRight" to "Move right",
            "leftButton" to "Left button", "rightButton" to "Right button")

        override fun groups(): List<RowGroup> = when (tab) {
            Tab.JOYSTICK -> listOf(RowGroup(null, guest.joystickControls.map { (id, label) ->
                SettingsRow.option("joy:$id", label, null, { joystick == id }) { joystick = id; update() }
            }, guest.joystickHelp))
            Tab.MOUSE -> listOfNotNull(RowGroup(null, mouseOptions.map { (id, label) ->
                SettingsRow.option("mouse:$id", label, null, { mouse == id }) { mouse = id; update() }
            }, "Stick directions use analog speed. Buttons and D-pad directions move at full speed."),
                mouse?.takeIf { it.startsWith("move") }?.let {
                    RowGroup(null, listOf(SettingsRow.slider("speed", "Speed",
                        { java.lang.String.format(java.util.Locale.ROOT, "%.1f×", speed / 10f) },
                        SliderSpec(1, 200, 1, 5, { speed }) { speed = it; update() })),
                        "Speed at full stick deflection.")
                })
            Tab.ACTION -> listOf(RowGroup(null, guest.actions.map { (id, label) ->
                SettingsRow.option("action:$id", label, null, { action == id }) { action = id; update() }
            }))
            else -> emptyList()
        }

        override fun createView(): View {
            val page = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Ui.BG)
            }
            page.addView(header(title, scope(game)), LinearLayout.LayoutParams(-1, dp(56)))
            val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            tabRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(8), 0, dp(4))
            }
            tabs.forEachIndexed { index, item ->
                val view = Ui.text(context, item.label, Ui.SECONDARY, bold = true).apply {
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    isClickable = true
                    setOnClickListener { selectTab(item) }
                }
                tabViews += item to view
                tabRow.addView(view, LinearLayout.LayoutParams(0, dp(40), 1f).apply { if (index > 0) marginStart = dp(6) })
            }
            body.addView(tabRow)
            val summaryLine = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(14), dp(14), dp(8))
            }
            summary = Ui.text(context, "", Ui.TITLE, bold = true).apply {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            summaryLine.addView(summary, LinearLayout.LayoutParams(0, -2, 1f))
            hint = Ui.text(context, "", Ui.SECONDARY, Ui.TEXT_MUTED)
            summaryLine.addView(hint, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(12) })
            body.addView(summaryLine)
            keyArea = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            body.addView(keyArea)
            body.addView(rowsView())
            scrollView = PageParts.scroll(this, body, intArrayOf(dp(18), 0, dp(18), dp(24)))
            page.addView(scrollView, LinearLayout.LayoutParams(-1, 0, 1f))
            footer = Footer(this) {
                listOf(FooterButton("clear", { "Clear" }) { finish(null) },
                    FooterButton("save", { saveLabel() }, ButtonTone.PRIMARY) { trySave() })
            }
            footer.render()
            page.addView(footer.bar, LinearLayout.LayoutParams(-1, -2))
            renderTab()
            return page
        }

        private fun saveLabel() = when (tab) {
            Tab.KEYS -> if (keys.isEmpty()) "Save keys" else "Save ${keys.size} key" + if (keys.size == 1) "" else "s"
            Tab.CYCLE -> "Save key cycle"
            else -> "Save"
        }

        private fun selectTab(next: Tab) {
            if (tab == next) return
            tab = next
            keyTab = 0
            renderTab()
            refresh()
            renav()
            focus("tab:${next.name}", reveal = false)
        }

        private fun renderTab() {
            tabViews.forEach { (item, view) ->
                val selected = item == tab
                view.setTextColor(if (selected) Ui.ON_ACCENT else Ui.TEXT_MUTED)
                view.background = Ui.focusable(context, if (selected) Ui.ACCENT else Ui.SURFACE,
                    if (selected) null else Ui.LINE)
            }
            keyArea.removeAllViews()
            subTabViews.clear()
            keyViews.clear()
            if (tab == Tab.KEYS || tab == Tab.CYCLE) {
                val pills = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 0, 0, dp(8))
                }
                keyTabs.forEachIndexed { index, (label, _) ->
                    val pill = Ui.text(context, label, Ui.SECONDARY).apply {
                        gravity = Gravity.CENTER
                        setPadding(dp(14), 0, dp(14), 0)
                        isClickable = true
                        setOnClickListener { keyTab = index; renderTab(); renav(); focus("sub:$index", reveal = false) }
                    }
                    subTabViews += pill
                    pills.addView(pill, LinearLayout.LayoutParams(-2, dp(32)).apply { if (index > 0) marginStart = dp(6) })
                }
                keyArea.addView(pills)
                keyTabs.getOrNull(keyTab)?.second?.forEachIndexed { rowIndex, row ->
                    val line = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                    val total = row.sumOf { it.weight.toDouble() }.toFloat()
                    var before = 0f
                    row.forEachIndexed { index, key ->
                        val holder = KeyView(key, rowIndex, (before + key.weight / 2) / total)
                        before += key.weight
                        keyViews += holder
                        line.addView(holder.view, LinearLayout.LayoutParams(0, dp(38), key.weight).apply {
                            if (index > 0) marginStart = dp(4)
                        })
                    }
                    keyArea.addView(line, LinearLayout.LayoutParams(-1, -2).apply { if (rowIndex > 0) topMargin = dp(4) })
                }
                keyArea.addView(Ui.text(context, keysFoot(), Ui.SECONDARY, Ui.TEXT_MUTED).apply {
                    setPadding(dp(14), dp(10), dp(14), 0)
                })
            }
            update()
        }

        private fun keysFoot(): String {
            if (tab == Tab.KEYS) return "Modifiers are pressed before the other keys."
            val (back, forward) = ControllerKeyCycles.pair(input) ?: return ""
            fun short(id: String) = VirtualControls.groups.flatMap { it.controls }
                .firstOrNull { it.id == id.removePrefix("virtual:") }?.short ?: id
            return "${short(back)} cycles backward and ${short(forward)} forward, wrapping around. " +
                "This sends keys, so it cannot see the game’s current selection."
        }

        private fun toggle(code: Int) {
            val list = if (tab == Tab.CYCLE) cycle else keys
            val limit = if (tab == Tab.CYCLE) 16 else 4
            if (!list.remove(code)) {
                if (list.size >= limit) {
                    message(if (tab == Tab.CYCLE) "A key cycle holds up to 16 keys" else "Choose up to four keys")
                    return
                }
                list += code
            }
            update()
        }

        /** Refresh the summary, keys, options, and footer after a change in the draft. */
        private fun update() {
            val list = if (tab == Tab.CYCLE) cycle else keys
            subTabViews.forEachIndexed { index, pill ->
                val selected = index == keyTab
                pill.setTextColor(if (selected) Ui.TEXT else Ui.TEXT_MUTED)
                pill.background = pillBackground(selected)
            }
            keyViews.forEach { holder ->
                val position = list.indexOf(holder.key.code)
                val selected = position >= 0
                val action = holder.key.label.length > 1
                holder.number.text = if (selected && (tab == Tab.CYCLE || list.size > 1)) "${position + 1}" else ""
                holder.label.setTextColor(if (selected) Ui.ON_ACCENT else Ui.TEXT)
                holder.view.background = Ui.focusable(context, when {
                    selected -> Ui.ACCENT
                    action -> Ui.SURFACE
                    else -> Ui.RAISED
                }, if (selected) null else if (action) Ui.LINE else null)
            }
            val nothing: Boolean
            when (tab) {
                Tab.KEYS, Tab.CYCLE -> {
                    nothing = list.isEmpty()
                    summary.text = if (nothing) "No keys selected"
                        else list.joinToString(if (tab == Tab.CYCLE) " → " else " + ") { guest.keyLabel(it) }
                    hint.text = if (tab == Tab.CYCLE) "2 to 16 keys, in order"
                        else if (nothing) "Up to 4 keys"
                        else list.joinToString(" + ") { "0x" + it.toString(16).padStart(2, '0') }
                }
                Tab.JOYSTICK -> {
                    nothing = joystick == null
                    summary.text = joystick?.let { id -> "Joystick " +
                        (guest.joystickControls.firstOrNull { it.first == id }?.second?.lowercase() ?: id) } ?: "Nothing selected"
                    hint.text = ""
                }
                Tab.MOUSE -> {
                    nothing = mouse == null
                    summary.text = mouse?.let { targetLabel(ControllerBinding(input, mouse = it, mouseSpeed = speed / 10f)) }
                        ?: "Nothing selected"
                    hint.text = ""
                }
                Tab.ACTION -> {
                    nothing = action == null
                    summary.text = action?.let { id -> guest.actions.firstOrNull { it.first == id }?.second ?: id }
                        ?: "Nothing selected"
                    hint.text = ""
                }
            }
            summary.setTextColor(if (nothing) Ui.TEXT_FAINT else Ui.ACCENT_SOFT)
            hint.visibility = if (hint.text.isNullOrEmpty()) View.GONE else View.VISIBLE
            if (::footer.isInitialized) footer.render()
            if (inStack) {
                refresh()
                renav()
            }
        }

        /** Key picker tabs are fully rounded pills. */
        private fun pillBackground(selected: Boolean) = android.graphics.drawable.StateListDrawable().apply {
            fun shape(focused: Boolean) = android.graphics.drawable.GradientDrawable().apply {
                setColor(if (selected) Ui.SELECTED else Color.TRANSPARENT)
                cornerRadius = dp(16).toFloat()
                if (focused) setStroke(dp(2), Color.WHITE) else if (!selected) setStroke(dp(1), Ui.LINE)
            }
            addState(intArrayOf(android.R.attr.state_selected), shape(true))
            addState(intArrayOf(android.R.attr.state_pressed), shape(true))
            addState(intArrayOf(), shape(false))
        }

        override fun navItems(): List<NavItem> {
            val items = ArrayList<NavItem>()
            tabViews.forEachIndexed { index, (item, view) ->
                items += NavItem("tab:${item.name}", view, 0, (index + 0.5f) / tabViews.size, { selectTab(item) })
            }
            subTabViews.forEachIndexed { index, view ->
                items += NavItem("sub:$index", view, 1, (index + 0.5f) / 8f, { view.performClick() })
            }
            keyViews.forEachIndexed { index, holder ->
                items += NavItem("key:$keyTab:$index", holder.view, 2 + holder.row, holder.x, { toggle(holder.key.code) })
            }
            items += rowNav(100)
            items += footer.nav(Int.MAX_VALUE)
            return items
        }

        override fun initialFocus(items: List<NavItem>) = "tab:${tab.name}"

        private fun trySave() {
            val binding = when (tab) {
                Tab.KEYS -> {
                    if (keys.isEmpty()) { message("Choose at least one key"); return }
                    ControllerBinding(input, keys.sortedWith(compareBy<Int> { it !in guest.modifierCodes }.thenBy { it }))
                }
                Tab.CYCLE -> {
                    if (cycle.size !in 2..16) { message("Choose 2 to 16 keys"); return }
                    null
                }
                Tab.JOYSTICK -> ControllerBinding(input, joystick = joystick ?: run { message("Choose a joystick control"); return })
                Tab.MOUSE -> ControllerBinding(input, mouse = mouse ?: run { message("Choose a mouse control"); return },
                    mouseSpeed = if (mouse!!.startsWith("move")) speed / 10f else 1f)
                Tab.ACTION -> ControllerBinding(input, action = action ?: run { message("Choose an action"); return })
            }
            finish(binding, cycle = tab == Tab.CYCLE)
        }

        /** Save the draft, or clear the control when [binding] is null and this is not a cycle. */
        private fun finish(binding: ControllerBinding?, cycle: Boolean = false) {
            try {
                val current = load(game)
                val updated = when {
                    cycle -> {
                        val (back, forward) = requireNotNull(ControllerKeyCycles.pair(input))
                        current.filterNot { it.input == back || it.input == forward } +
                            ControllerBinding(back, cycleKeys = this.cycle.toList()) +
                            ControllerBinding(forward, cycleKeys = this.cycle.toList())
                    }
                    binding == null -> current.filterNot { it.input == input }
                    else -> current.filterNot { it.input == input } + binding
                }
                validateBindings(updated)
                save(game, updated)
                changed()
            } catch (error: Exception) {
                message(error.message ?: "Could not save mapping")
                return
            }
            if (pages.top === this) {
                pages.pop()
                pages.flash("m:" + input.removePrefix("virtual:"))
            }
        }
    }
}
