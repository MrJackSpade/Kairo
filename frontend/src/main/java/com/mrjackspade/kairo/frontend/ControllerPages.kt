package com.mrjackspade.kairo.frontend

import android.app.Activity
import android.content.Context
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
import androidx.annotation.StringRes
import kotlin.math.roundToInt

/** What a guest machine accepts from a controller: its keys, joystick, and app actions. */
data class ControllerGuestSpec(
    val name: String,
    val keyCodes: List<Int>,
    /** A key's legend, as the guest keyboard prints it. */
    val keyLabel: (Int) -> String,
    val modifierCodes: Set<Int>,
    val joystickControls: List<Pair<String, UiText>>,
    val joystickHelp: UiText,
    val actions: List<Pair<String, UiText>>,
    /** Keys are picked on the guest keyboard layout; codes it lacks go on an Other tab. */
    val keyboard: GuestKeyboardLayout,
    /** How the joystick tab lays out [joystickControls]: one port of up/down/left/right and buttons, unless an app says otherwise. */
    val joystickPorts: List<JoystickPort> = JoystickPort.single(joystickControls.map { it.first })
) {
    fun joystickLabel(context: Context, id: String): String =
        joystickControls.firstOrNull { it.first == id }?.second?.resolve(context) ?: id

    /** A joystick control as a binding names it, such as "Joystick fire 1". */
    fun joystickTarget(context: Context, id: String): String =
        context.getString(R.string.kairo_binding_joystick, joystickLabel(context, id).lowercase())

    fun actionLabel(context: Context, id: String): String =
        actions.firstOrNull { it.first == id }?.second?.resolve(context) ?: id.replaceFirstChar(Char::uppercase)

    /** What a binding sends, as the controller pages and the keyboard panel name it. */
    fun describe(context: Context, binding: ControllerBinding): String = when {
        binding.cycleKeys.isNotEmpty() -> context.getString(
            if (binding.input == ControllerKeyCycles.pair(binding.input)?.first) R.string.kairo_binding_cycle_previous
            else R.string.kairo_binding_cycle_next,
            binding.cycleKeys.joinToString(" → ") { keyLabel(it) })
        binding.mouse != null -> {
            val target = MOUSE_TARGETS[binding.mouse]?.let(context::getString)
                ?: context.getString(R.string.kairo_binding_mouse_other, binding.mouse.removePrefix("move").lowercase())
            if (binding.mouse.startsWith("move")) context.getString(R.string.kairo_binding_mouse_speed, target, binding.mouseSpeed)
            else target
        }
        binding.action != null -> actionLabel(context, binding.action)
        else -> (binding.keys.map { keyLabel(it) } +
            listOfNotNull(binding.joystick?.let { joystickTarget(context, it) })).joinToString(" + ")
    }

    companion object {
        /** The app actions every Kairo app offers a controller. */
        val STANDARD_ACTIONS = listOf("menu" to UiText.res(R.string.kairo_action_menu),
            "pause" to UiText.res(R.string.kairo_action_pause),
            "fastForward" to UiText.res(R.string.kairo_action_fast_forward),
            "restart" to UiText.res(R.string.kairo_action_restart), "exit" to UiText.res(R.string.kairo_action_exit))

        /** What a mouse binding does, by its stored name. */
        val MOUSE_TARGETS = mapOf("moveUp" to R.string.kairo_binding_mouse_up,
            "moveDown" to R.string.kairo_binding_mouse_down, "moveLeft" to R.string.kairo_binding_mouse_left,
            "moveRight" to R.string.kairo_binding_mouse_right, "leftButton" to R.string.kairo_binding_mouse_left_button,
            "rightButton" to R.string.kairo_binding_mouse_right_button)
    }
}

/** One guest joystick port: its direction controls by direction, and its buttons in order. */
data class JoystickPort(val label: UiText?, val up: String?, val down: String?, val left: String?,
                        val right: String?, val buttons: List<String>) {
    companion object {
        /** One unnamed port: controls named up/down/left/right are its directions, the rest its buttons. */
        fun single(controls: List<String>): List<JoystickPort> {
            fun direction(name: String) = controls.firstOrNull { it == name }
            val directions = setOf("up", "down", "left", "right")
            return listOf(JoystickPort(null, direction("up"), direction("down"), direction("left"), direction("right"),
                controls.filter { it !in directions }))
        }
    }
}

/** The virtual controls in the one order every controller page uses. */
internal object VirtualControls {
    class Control(val id: String, @StringRes val short: Int, @StringRes val full: Int)
    class Group(@StringRes val label: Int, val stick: Boolean, val controls: List<Control>)

    val groups = listOf(
        Group(R.string.kairo_control_group_dpad, false, listOf(
            Control("up", R.string.kairo_up, R.string.kairo_control_dpad_up),
            Control("down", R.string.kairo_down, R.string.kairo_control_dpad_down),
            Control("left", R.string.kairo_left, R.string.kairo_control_dpad_left),
            Control("right", R.string.kairo_right, R.string.kairo_control_dpad_right))),
        Group(R.string.kairo_control_group_face, false, listOf(
            Control("a", R.string.kairo_control_a, R.string.kairo_control_a_button),
            Control("b", R.string.kairo_control_b, R.string.kairo_control_b_button),
            Control("x", R.string.kairo_control_x, R.string.kairo_control_x_button),
            Control("y", R.string.kairo_control_y, R.string.kairo_control_y_button))),
        Group(R.string.kairo_control_group_shoulders, false, listOf(
            Control("l1", R.string.kairo_control_l1, R.string.kairo_control_l1_button),
            Control("r1", R.string.kairo_control_r1, R.string.kairo_control_r1_button),
            Control("l2", R.string.kairo_control_l2, R.string.kairo_control_l2_button),
            Control("r2", R.string.kairo_control_r2, R.string.kairo_control_r2_button))),
        Group(R.string.kairo_control_group_system, false, listOf(
            Control("start", R.string.kairo_control_start, R.string.kairo_control_start_button),
            Control("select", R.string.kairo_control_select, R.string.kairo_control_select_button),
            Control("menu", R.string.kairo_control_menu, R.string.kairo_control_menu_button))),
        Group(R.string.kairo_control_group_left_stick, true, listOf(
            Control("lsup", R.string.kairo_up, R.string.kairo_control_left_stick_up),
            Control("lsdown", R.string.kairo_down, R.string.kairo_control_left_stick_down),
            Control("lsleft", R.string.kairo_left, R.string.kairo_control_left_stick_left),
            Control("lsright", R.string.kairo_right, R.string.kairo_control_left_stick_right))),
        Group(R.string.kairo_control_group_right_stick, true, listOf(
            Control("rsup", R.string.kairo_up, R.string.kairo_control_right_stick_up),
            Control("rsdown", R.string.kairo_down, R.string.kairo_control_right_stick_down),
            Control("rsleft", R.string.kairo_left, R.string.kairo_control_right_stick_left),
            Control("rsright", R.string.kairo_right, R.string.kairo_control_right_stick_right))))

    fun full(context: Context, id: String) =
        groups.flatMap { it.controls }.firstOrNull { it.id == id }?.let { context.getString(it.full) } ?: id
}

/** A physical input the Choose input page offers, grouped by where it is on a controller. */
internal class InputSource(val group: String, val label: String, val name: String, val input: String)

internal object InputSources {
    fun common(context: Context): List<InputSource> {
        fun text(id: Int, vararg args: Any) = context.getString(id, *args)
        val buttonsGroup = text(R.string.kairo_input_group_buttons)
        val buttons = listOf("A" to KeyEvent.KEYCODE_BUTTON_A, "B" to KeyEvent.KEYCODE_BUTTON_B,
            "X" to KeyEvent.KEYCODE_BUTTON_X, "Y" to KeyEvent.KEYCODE_BUTTON_Y,
            "L1" to KeyEvent.KEYCODE_BUTTON_L1, "R1" to KeyEvent.KEYCODE_BUTTON_R1,
            "L2" to KeyEvent.KEYCODE_BUTTON_L2, "R2" to KeyEvent.KEYCODE_BUTTON_R2
        ).map { (label, code) -> InputSource(buttonsGroup, label, text(R.string.kairo_input_button, label), "button:$code") } +
            listOf(R.string.kairo_control_start to KeyEvent.KEYCODE_BUTTON_START,
                R.string.kairo_control_select to KeyEvent.KEYCODE_BUTTON_SELECT,
                R.string.kairo_input_mode to KeyEvent.KEYCODE_BUTTON_MODE,
                R.string.kairo_input_left_stick_press to KeyEvent.KEYCODE_BUTTON_THUMBL,
                R.string.kairo_input_right_stick_press to KeyEvent.KEYCODE_BUTTON_THUMBR
            ).map { (label, code) -> InputSource(buttonsGroup, text(label), text(label), "button:$code") }
        val dpadGroup = text(R.string.kairo_input_group_dpad_buttons)
        val dpad = listOf(Triple(R.string.kairo_up, R.string.kairo_control_dpad_up, KeyEvent.KEYCODE_DPAD_UP),
            Triple(R.string.kairo_down, R.string.kairo_control_dpad_down, KeyEvent.KEYCODE_DPAD_DOWN),
            Triple(R.string.kairo_left, R.string.kairo_control_dpad_left, KeyEvent.KEYCODE_DPAD_LEFT),
            Triple(R.string.kairo_right, R.string.kairo_control_dpad_right, KeyEvent.KEYCODE_DPAD_RIGHT)
        ).map { (label, name, code) -> InputSource(dpadGroup, text(label), text(name), "button:$code") }
        /** A stick's four directions; [names] are its up, down, left and right. */
        fun axes(group: Int, names: List<Int>, x: Int, y: Int, kind: String = "axis") =
            listOf("$kind:$y:-", "$kind:$y:+", "$kind:$x:-", "$kind:$x:+").zip(names).zip(DIRECTIONS)
                .map { (input, direction) -> InputSource(text(group), text(direction), text(input.second), input.first) }
        return buttons + dpad +
            axes(R.string.kairo_control_group_left_stick, listOf(R.string.kairo_control_left_stick_up,
                R.string.kairo_control_left_stick_down, R.string.kairo_control_left_stick_left,
                R.string.kairo_control_left_stick_right), MotionEvent.AXIS_X, MotionEvent.AXIS_Y) +
            axes(R.string.kairo_control_group_right_stick, listOf(R.string.kairo_control_right_stick_up,
                R.string.kairo_control_right_stick_down, R.string.kairo_control_right_stick_left,
                R.string.kairo_control_right_stick_right), MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ) +
            listOf(R.string.kairo_input_left_trigger to MotionEvent.AXIS_LTRIGGER,
                R.string.kairo_input_right_trigger to MotionEvent.AXIS_RTRIGGER).map { (label, axis) ->
                InputSource(text(R.string.kairo_input_group_triggers), text(label), text(label), "axis:$axis:+")
            } +
            axes(R.string.kairo_input_group_hat, listOf(R.string.kairo_input_hat_up, R.string.kairo_input_hat_down,
                R.string.kairo_input_hat_left, R.string.kairo_input_hat_right), MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y, "hat")
    }

    private val DIRECTIONS = listOf(R.string.kairo_up, R.string.kairo_down, R.string.kairo_left, R.string.kairo_right)

    /** Axes a connected controller has beyond the common ones, grouped under its name. */
    fun extra(context: Context): List<InputSource> {
        val known = common(context).map { it.input }.toSet()
        return InputDevice.getDeviceIds().toList().mapNotNull(InputDevice::getDevice)
            .filter { it.supportsSource(InputDevice.SOURCE_JOYSTICK) }.flatMap { device ->
                device.motionRanges.map { it.axis }.distinct()
                    .flatMap { axis -> listOf(axisInput(axis, -1f), axisInput(axis, 1f)) }
                    .filter { it !in known }
                    .map { InputSource(device.name.uppercase(), name(context, it), name(context, it), it) }
            }.distinctBy { it.input }
    }

    fun axisInput(axis: Int, value: Float): String {
        val kind = if (axis == MotionEvent.AXIS_HAT_X || axis == MotionEvent.AXIS_HAT_Y) "hat" else "axis"
        return kind + ":" + axis + ":" + if (value > 0) "+" else "-"
    }

    /** A readable name for any input code, such as Button A or Axis GAS +. */
    fun name(context: Context, input: String): String {
        common(context).firstOrNull { it.input == input }?.let { return it.name }
        val parts = input.split(':')
        if (parts.size == 2 && parts[0] == "button") {
            val code = parts[1].toIntOrNull() ?: return input
            val key = KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
            return if (key.startsWith("BUTTON_")) context.getString(R.string.kairo_input_button, key.removePrefix("BUTTON_"))
                else key.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
        }
        if (parts.size == 3) {
            val axis = parts[1].toIntOrNull() ?: return input
            return context.getString(if (parts[0] == "hat") R.string.kairo_input_hat_code else R.string.kairo_input_axis_code,
                MotionEvent.axisToString(axis).removePrefix("AXIS_"), parts[2])
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
    /** Where a game's bindings come from: GameSource.ALL_GAMES, CUSTOM, or a catalog's name. */
    private val gameSource: (Game) -> String,
    private val onScreen: OnScreenControls,
    private val touch: TouchSettingsCoordinator<Game>,
    /** Called after any mapping change, so the running game uses it at once. */
    private val changed: () -> Unit = {}
) {
    private fun message(text: String) = Ui.message(activity, text)
    private fun str(id: Int, vararg args: Any): String = activity.getString(id, *args)
    /** A source key (GameSource) in the player's language, for a row's value. */
    private fun shown(source: String) = GameSource.text(activity, source)

    private fun sticks() = configuration?.layout != ControllerLayout.WITHOUT_STICKS

    /**
     * Controls, for all games. In a game with its own bindings, that game's mapping comes first and
     * the all-games one is named as such, so neither reads as what the game uses when it is not.
     */
    fun controlsPage(running: Game?) = GroupsPage(str(R.string.kairo_controls), { GameSource.ALL_GAMES }, {
        val own = running?.takeIf { gameSource(it) != GameSource.ALL_GAMES }
        val foot = running?.let { game ->
            if (own != null) str(R.string.kairo_controls_foot_own_mapping, gameTitle(game))
            else str(R.string.kairo_controls_foot_all_games_mapping, gameTitle(game))
        }
        listOf(
            RowGroup(str(R.string.kairo_controls_group_controller), listOfNotNull(
                configuration?.let { layoutRow(it) },
                own?.let { game ->
                    SettingsRow.nav("running-mapping", str(R.string.kairo_controls_game_mapping, gameTitle(game)), value = { shown(gameSource(game)) },
                        tone = { if (gameSource(game) == GameSource.CUSTOM) ValueTone.CUSTOM else ValueTone.SUMMARY }) {
                        pages.push(mappingPage(game))
                    }
                },
                SettingsRow.nav("mapping", str(if (own != null) R.string.kairo_controls_all_games_mapping else R.string.kairo_button_mapping),
                    value = { shown(if (globalCustom()) GameSource.CUSTOM else GameSource.BUILT_IN) },
                    tone = { if (globalCustom()) ValueTone.CUSTOM else ValueTone.SUMMARY }) {
                    pages.push(mappingPage(null))
                },
                SettingsRow.nav("inputs", str(R.string.kairo_controller_inputs),
                    value = { str(if (physicalCustom()) R.string.kairo_custom else R.string.kairo_standard) },
                    tone = { if (physicalCustom()) ValueTone.CUSTOM else ValueTone.SUMMARY }) {
                    pages.push(inputsPage())
                },
                SettingsRow.slider("dead-zone", str(R.string.kairo_controls_dead_zone), { str(R.string.kairo_percent, (getDeadZone() * 100).roundToInt()) },
                    SliderSpec(10, 90, 1, 5, { (getDeadZone() * 100).roundToInt() }) {
                        setDeadZone(it / 100f)
                    })), foot),
            touch.globalGroup(),
            onScreen.group())
    })

    private fun layoutRow(config: ControllerConfiguration): SettingsRow {
        val spec = ChoiceSpec(str(R.string.kairo_controller_layout), { GameSource.ALL_GAMES }, {
            val detected = ControllerCapabilities.detect(ControllerCapabilities.connected())
            listOf(ChoiceGroup(null, listOfNotNull(
                detected.device?.let {
                    ChoiceOption(ControllerConfiguration.Choice.AUTO.name, UiText.res(R.string.kairo_auto),
                        UiText.res(R.string.kairo_controller_layout_detected, UiText.res(detected.layout.detected)))
                },
                ChoiceOption(ControllerConfiguration.Choice.WITH_STICKS.name, UiText.res(ControllerConfiguration.Choice.WITH_STICKS.label)),
                ChoiceOption(ControllerConfiguration.Choice.WITHOUT_STICKS.name, UiText.res(ControllerConfiguration.Choice.WITHOUT_STICKS.label)))))
        }, { config.choice.name }, select = { picked ->
            config.select(ControllerConfiguration.Choice.valueOf(picked))
            onConfigurationChanged()
            changed()
        })
        return SettingsRow.choice("layout", str(R.string.kairo_controller_layout), spec, value = { config.label(activity) })
    }

    /** Game settings › Button mapping for one game. */
    fun gameRow(game: Game, enabled: () -> Boolean, source: () -> String) =
        SettingsRow.nav("game-mapping", str(R.string.kairo_button_mapping), value = { shown(source()) },
            tone = { if (source() == GameSource.CUSTOM) ValueTone.CUSTOM else ValueTone.SUMMARY },
            enabled = enabled, disabledReason = { str(R.string.kairo_controls_hash_first) }) {
            pages.push(mappingPage(game))
        }

    private fun scope(game: Game?) = game?.let(gameTitle) ?: GameSource.ALL_GAMES

    fun mappingPage(game: Game?) = GroupsPage(str(R.string.kairo_button_mapping), { scope(game) }, {
        val bindings = load(game).associateBy { it.input }
        val groups = VirtualControls.groups.filter { sticks() || !it.stick }.mapIndexed { index, group ->
            RowGroup(str(group.label), group.controls.map { control ->
                val bound = bindings["virtual:${control.id}"]
                val inherited = if (control.id.startsWith("ls")) bindings["virtual:" + control.id.removePrefix("ls")] else null
                SettingsRow.nav("m:${control.id}", str(control.short),
                    value = { bound?.let(::targetLabel) ?: inherited?.let { str(R.string.kairo_mapping_follows_dpad, targetLabel(it)) }
                        ?: str(R.string.kairo_unassigned) },
                    tone = { when {
                        bound != null -> ValueTone.CURRENT
                        inherited != null -> ValueTone.SUMMARY
                        else -> ValueTone.EMPTY
                    } }) {
                    pages.push(TargetPage(game, "virtual:${control.id}", str(control.full)))
                }
            }, if (index == 0) mappingStatus(game) else null)
        }.toMutableList()
        val legacy = bindings.values.filter { !it.input.startsWith("virtual:") }
        if (legacy.isNotEmpty()) groups += RowGroup(str(R.string.kairo_mapping_group_legacy), legacy.map { binding ->
            SettingsRow.nav("m:${binding.input}", InputSources.name(activity, binding.input), value = { targetLabel(binding) },
                tone = { ValueTone.CURRENT }) {
                pages.push(TargetPage(game, binding.input, InputSources.name(activity, binding.input)))
            }
        }, str(R.string.kairo_mapping_legacy_foot))
        groups += RowGroup(null, listOf(SettingsRow.action("m:reset", str(R.string.kairo_mapping_reset),
            confirm = Confirm(str(if (game == null) R.string.kairo_mapping_reset_all_confirm
                else R.string.kairo_mapping_reset_game_confirm), str(R.string.kairo_reset)) {
                change(str(R.string.kairo_mapping_reset_done)) { reset(game) }
            })))
        groups
    })

    /** Controller inputs: which physical input drives each virtual control, for all games. */
    fun inputsPage() = GroupsPage(str(R.string.kairo_controller_inputs), { GameSource.ALL_GAMES }, {
        val bindings = loadPhysical().groupBy { it.control }
        val connected = InputDevice.getDeviceIds().toList().mapNotNull(InputDevice::getDevice)
            .filter { it.supportsSource(InputDevice.SOURCE_GAMEPAD) || it.supportsSource(InputDevice.SOURCE_JOYSTICK) }
            .joinToString { it.name }
        listOf(RowGroup(str(R.string.kairo_inputs_group_connected), listOf(SettingsRow.info("connected", str(R.string.kairo_inputs_controller)) {
            connected.ifEmpty { str(R.string.kairo_inputs_none_connected) }
        }))) + VirtualControls.groups.map { group ->
            RowGroup(str(group.label), group.controls.map { control ->
                val sources = bindings[control.id].orEmpty()
                SettingsRow.action("p:${control.id}", str(control.short),
                    value = { when (sources.size) {
                        0 -> str(R.string.kairo_unassigned)
                        1 -> InputSources.name(activity, sources[0].input)
                        else -> activity.resources.getQuantityString(R.plurals.kairo_inputs_and_more, sources.size - 1,
                            InputSources.name(activity, sources[0].input), sources.size - 1)
                    } },
                    tone = { if (sources.isEmpty()) ValueTone.EMPTY else ValueTone.CURRENT },
                    tray = { listenTray(control.id, sources.isNotEmpty()) })
            })
        } + RowGroup(null, listOf(
            SettingsRow.action("p:add", str(R.string.kairo_inputs_add_alternate), tray = { listenTray(null, false) }),
            SettingsRow.action("p:reset", str(R.string.kairo_inputs_reset),
                confirm = Confirm(str(R.string.kairo_inputs_reset_confirm), str(R.string.kairo_reset)) {
                    change(str(R.string.kairo_inputs_reset_done)) { resetPhysical() }
                })))
    })

    /** Listening starts when the tray opens; Enter code and Clear sit in the same tray. */
    private fun listenTray(control: String?, assigned: Boolean): TraySpec = TraySpec(
        str(if (control == null) R.string.kairo_inputs_listening_alternate else R.string.kairo_inputs_listening),
        listOfNotNull(
            TrayButton(str(R.string.kairo_cancel)),
            if (control != null && assigned) TrayButton(str(R.string.kairo_clear)) {
                change(null) { savePhysical(loadPhysical().filterNot { it.control == control }) }
            } else null,
            TrayButton(str(R.string.kairo_inputs_choose_input)) { pages.push(chooseInputPage(control)) },
            TrayButton(str(R.string.kairo_inputs_enter_code), keepOpen = true) { pages.replaceTray(codeTray(control)) }),
        listening = true, focus = 0, capture = PhysicalInputCapture { input ->
            pages.closeTray()
            assign(control, input)
        })

    private fun codeTray(control: String?) = TraySpec(
        str(R.string.kairo_inputs_code_help),
        listOf(TrayButton(str(R.string.kairo_cancel)), TrayButton(str(R.string.kairo_inputs_assign), ButtonTone.PRIMARY, keepOpen = true) { text ->
            val input = text.trim().lowercase()
            try {
                PhysicalControllerBindings.toJson(listOf(PhysicalControllerBinding(input, "a")))
            } catch (_: Exception) {
                message(str(R.string.kairo_inputs_invalid_code))
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
        val sources = InputSources.common(activity) + InputSources.extra(activity)
        val spec = ChoiceSpec(str(R.string.kairo_inputs_choose_input), {
                control?.let { VirtualControls.full(activity, it) } ?: str(R.string.kairo_inputs_alternate_input) },
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

    private fun actsAsPage(input: String) = ChoicePage(ChoiceSpec(str(R.string.kairo_inputs_acts_as), { InputSources.name(activity, input) }, {
        VirtualControls.groups.map { group ->
            ChoiceGroup(str(group.label), group.controls.map { ChoiceOption(it.id, UiText.res(it.full)) })
        }
    }, { loadPhysical().firstOrNull { it.input == input }?.control }, { control ->
        val name = InputSources.name(activity, input)
        change(str(R.string.kairo_inputs_added, name, VirtualControls.full(activity, control))) {
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
        } catch (error: Exception) { message(error.message ?: str(R.string.kairo_mapping_save_failed)) }
        pages.refresh()
    }

    private fun targetLabel(binding: ControllerBinding): String = guest.describe(activity, binding)

    private fun joystickLabel(id: String) = guest.joystickTarget(activity, id)

    private enum class Tab(@StringRes val label: Int) { KEYS(R.string.kairo_target_tab_keys), CYCLE(R.string.kairo_target_tab_cycle),
        JOYSTICK(R.string.kairo_joystick), MOUSE(R.string.kairo_mouse), ACTION(R.string.kairo_target_tab_action) }

    private class Key(val code: Int, val label: String, val weight: Float)

    /** The pages of the key picker: the guest keyboard, then codes it does not show. */
    private val keyTabs: List<Pair<String, List<List<Key>>>> by lazy {
        val plain = guest.keyboard.pages.flatMap { page -> page.rows.flatten().filter { !it.chordShift }.map { it.code } }.toSet()
        val tabs = guest.keyboard.pages.map { page ->
            page.pickerLabel.resolve(activity) to page.rows.map { row ->
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
        (if (other.isEmpty()) tabs else tabs + (str(R.string.kairo_target_keys_other) to other.chunked(8))).filter { it.second.isNotEmpty() }
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

        private val mouseOptions = listOf("moveUp" to R.string.kairo_target_mouse_up, "moveDown" to R.string.kairo_target_mouse_down,
            "moveLeft" to R.string.kairo_target_mouse_left, "moveRight" to R.string.kairo_target_mouse_right,
            "leftButton" to R.string.kairo_target_mouse_left_button, "rightButton" to R.string.kairo_target_mouse_right_button)

        override fun groups(): List<RowGroup> = when (tab) {
            Tab.JOYSTICK -> listOf(RowGroup(null, listOf(SettingsRow.option("joy:none", str(R.string.kairo_none), null,
                { joystick == null }) { joystick = null; update() }) + guest.joystickControls.map { (id, label) ->
                SettingsRow.option("joy:$id", label.resolve(activity), null, { joystick == id }) { joystick = id; update() }
            }, guest.joystickHelp.resolve(activity)))
            Tab.MOUSE -> listOfNotNull(RowGroup(null, mouseOptions.map { (id, label) ->
                SettingsRow.option("mouse:$id", str(label), null, { mouse == id }) { mouse = id; update() }
            }, str(R.string.kairo_target_mouse_foot)),
                mouse?.takeIf { it.startsWith("move") }?.let {
                    RowGroup(null, listOf(SettingsRow.slider("speed", str(R.string.kairo_target_mouse_speed),
                        { str(R.string.kairo_target_mouse_speed_value, speed / 10f) },
                        SliderSpec(1, 200, 1, 5, { speed }) { speed = it; update() })),
                        str(R.string.kairo_target_mouse_speed_foot))
                })
            Tab.ACTION -> listOf(RowGroup(null, guest.actions.map { (id, label) ->
                SettingsRow.option("action:$id", label.resolve(activity), null, { action == id }) { action = id; update() }
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
                val view = Ui.text(context, str(item.label), Ui.SECONDARY, bold = true).apply {
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
                listOf(FooterButton("clear", { str(R.string.kairo_clear) }) { finish(null) },
                    FooterButton("save", { saveLabel() }, ButtonTone.PRIMARY) { trySave() })
            }
            footer.render()
            page.addView(footer.bar, LinearLayout.LayoutParams(-1, -2))
            renderTab()
            return page
        }

        private fun saveLabel() = when (tab) {
            Tab.KEYS -> if (keys.isEmpty()) str(R.string.kairo_target_save_keys) else activity.resources.getQuantityString(
                if (joystick != null) R.plurals.kairo_target_save_keys_and_joystick else R.plurals.kairo_target_save_n_keys,
                keys.size, keys.size)
            Tab.CYCLE -> str(R.string.kairo_target_save_cycle)
            else -> str(R.string.kairo_save)
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
            if (tab == Tab.KEYS) return str(R.string.kairo_target_keys_foot)
            val (back, forward) = ControllerKeyCycles.pair(input) ?: return ""
            fun short(id: String) = VirtualControls.groups.flatMap { it.controls }
                .firstOrNull { it.id == id.removePrefix("virtual:") }?.let { str(it.short) } ?: id
            return str(R.string.kairo_target_cycle_foot, short(back), short(forward))
        }

        private fun toggle(code: Int) {
            val list = if (tab == Tab.CYCLE) cycle else keys
            val limit = if (tab == Tab.CYCLE) 16 else 4
            if (!list.remove(code)) {
                if (list.size >= limit) {
                    message(str(if (tab == Tab.CYCLE) R.string.kairo_target_cycle_limit else R.string.kairo_target_keys_limit))
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
                Tab.CYCLE -> {
                    nothing = list.isEmpty()
                    summary.text = if (nothing) str(R.string.kairo_target_no_keys) else list.joinToString(" → ") { guest.keyLabel(it) }
                    hint.text = str(R.string.kairo_target_cycle_hint)
                }
                Tab.KEYS -> {
                    nothing = list.isEmpty() && joystick == null
                    summary.text = if (nothing) str(R.string.kairo_target_no_keys)
                        else (list.map { guest.keyLabel(it) } + listOfNotNull(joystick?.let(::joystickLabel))).joinToString(" + ")
                    hint.text = if (list.isEmpty()) str(R.string.kairo_target_keys_hint)
                        else list.joinToString(" + ") { "0x" + it.toString(16).padStart(2, '0') }
                }
                Tab.JOYSTICK -> {
                    nothing = joystick == null && keys.isEmpty()
                    summary.text = if (nothing) str(R.string.kairo_target_nothing)
                        else (listOfNotNull(joystick?.let(::joystickLabel)) + keys.map { guest.keyLabel(it) }).joinToString(" + ")
                    hint.text = ""
                }
                Tab.MOUSE -> {
                    nothing = mouse == null
                    summary.text = mouse?.let { targetLabel(ControllerBinding(input, mouse = it, mouseSpeed = speed / 10f)) }
                        ?: str(R.string.kairo_target_nothing)
                    hint.text = ""
                }
                Tab.ACTION -> {
                    nothing = action == null
                    summary.text = action?.let { guest.actionLabel(activity, it) } ?: str(R.string.kairo_target_nothing)
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
                // Keys and a joystick input are one draft: either tab saves both.
                Tab.KEYS, Tab.JOYSTICK -> {
                    if (keys.isEmpty() && joystick == null) {
                        message(str(if (tab == Tab.KEYS) R.string.kairo_target_choose_key else R.string.kairo_target_choose_joystick)); return
                    }
                    ControllerBinding(input, keys.sortedWith(compareBy<Int> { it !in guest.modifierCodes }.thenBy { it }),
                        joystick = joystick)
                }
                Tab.CYCLE -> {
                    if (cycle.size !in 2..16) { message(str(R.string.kairo_target_choose_cycle)); return }
                    null
                }
                Tab.MOUSE -> ControllerBinding(input, mouse = mouse ?: run { message(str(R.string.kairo_target_choose_mouse)); return },
                    mouseSpeed = if (mouse!!.startsWith("move")) speed / 10f else 1f)
                Tab.ACTION -> ControllerBinding(input, action = action ?: run { message(str(R.string.kairo_target_choose_action)); return })
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
                message(error.message ?: str(R.string.kairo_mapping_save_failed))
                return
            }
            if (pages.top === this) {
                pages.pop()
                pages.flash("m:" + input.removePrefix("virtual:"))
            }
        }
    }
}
